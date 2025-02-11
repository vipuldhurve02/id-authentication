package io.mosip.authentication.common.service.integration;

import java.io.IOException;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.authentication.common.service.entity.ApiKeyData;
import io.mosip.authentication.common.service.entity.MispLicenseData;
import io.mosip.authentication.common.service.entity.PartnerData;
import io.mosip.authentication.common.service.entity.PartnerMapping;
import io.mosip.authentication.common.service.entity.PolicyData;
import io.mosip.authentication.common.service.repository.ApiKeyDataRepository;
import io.mosip.authentication.common.service.repository.MispLicenseDataRepository;
import io.mosip.authentication.common.service.repository.PartnerDataRepository;
import io.mosip.authentication.common.service.repository.PartnerMappingRepository;
import io.mosip.authentication.common.service.repository.PolicyDataRepository;
import io.mosip.authentication.common.service.transaction.manager.IdAuthSecurityManager;
import io.mosip.authentication.core.constant.IdAuthCommonConstants;
import io.mosip.authentication.core.constant.IdAuthenticationErrorConstants;
import io.mosip.authentication.core.exception.IdAuthenticationBusinessException;
import io.mosip.authentication.core.partner.dto.PartnerPolicyResponseDTO;
import io.mosip.authentication.core.partner.dto.PolicyDTO;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.websub.model.EventModel;

/**
 * This class Partner Service Manager connects to partner service to validate
 * and get the policy file for a given partnerID, partner api key and misp
 * license key.
 * 
 * @author Nagarjuna
 *
 */
@Component
@Transactional
public class PartnerServiceManager {

	/** The Constant API_KEY_DATA. */
	private static final String API_KEY_DATA = "apiKeyData";

	/** The Constant PARTNER_DATA. */
	private static final String PARTNER_DATA = "partnerData";

	/** The Constant POLICY_DATA. */
	private static final String POLICY_DATA = "policyData";

	/** The Constant MISP_LICENSE_DATA. */
	private static final String MISP_LICENSE_DATA = "mispLicenseData";

	/** The partner mapping repo. */
	@Autowired
	private PartnerMappingRepository partnerMappingRepo;

	/** The partner data repo. */
	@Autowired
	private PartnerDataRepository partnerDataRepo;

	/** The policy data repo. */
	@Autowired
	private PolicyDataRepository policyDataRepo;

	/** The api key repo. */
	@Autowired
	private ApiKeyDataRepository apiKeyRepo;

	/** The misp lic data repo. */
	@Autowired
	private MispLicenseDataRepository mispLicDataRepo;

	/** The mapper. */
	@Autowired
	private ObjectMapper mapper;

	/** The security manager. */
	@Autowired
	private IdAuthSecurityManager securityManager;

	/**
	 * Validate and get policy.
	 *
	 * @param partnerId the partner id
	 * @param partner_api_key the partner api key
	 * @param misp_license_key the misp license key
	 * @param certificateNeeded the certificate needed
	 * @return the partner policy response DTO
	 * @throws IdAuthenticationBusinessException the id authentication business exception
	 */
	public PartnerPolicyResponseDTO validateAndGetPolicy(String partnerId, String partner_api_key, String misp_license_key,
			boolean certificateNeeded) throws IdAuthenticationBusinessException {
		Optional<PartnerMapping> partnerMappingDataOptional = partnerMappingRepo.findByPartnerIdAndApiKeyId(partnerId, partner_api_key);
		Optional<MispLicenseData> mispLicOptional = mispLicDataRepo.findByLicenseKey(misp_license_key);
		validatePartnerMappingDetails(partnerMappingDataOptional, mispLicOptional);
		PartnerPolicyResponseDTO response = new PartnerPolicyResponseDTO();
		PartnerMapping partnerMapping = partnerMappingDataOptional.get();
		PartnerData partnerData = partnerMapping.getPartnerData();
		PolicyData policyData = partnerMapping.getPolicyData();
		ApiKeyData apiKeyData = partnerMapping.getApiKeyData();
		MispLicenseData mispLicenseData = mispLicOptional.get();
		response.setPolicyId(policyData.getPolicyId());
		response.setPolicyName(policyData.getPolicyName());
		response.setPolicy(mapper.convertValue(policyData.getPolicy(), PolicyDTO.class));
		response.setPolicyDescription(policyData.getPolicyDescription());
		response.setPolicyStatus(policyData.getPolicyStatus().contentEquals("ACTIVE"));
		response.setPartnerId(partnerData.getPartnerId());
		response.setPartnerName(partnerData.getPartnerName());
		if (certificateNeeded) {
			response.setCertificateData(partnerData.getCertificateData());
		}
		response.setPolicyExpiresOn(policyData.getPolicyExpiresOn());
		response.setApiKeyExpiresOn(apiKeyData.getApiKeyExpiresOn());
		response.setMispExpiresOn(mispLicenseData.getMispExpiresOn());
		return response;
	}

	/**
	 * Validate partner mapping details.
	 *
	 * @param partnerMappingDataOptional the partner mapping data optional
	 * @param mispLicOptional the misp lic optional
	 * @throws IdAuthenticationBusinessException the id authentication business exception
	 */
	private void validatePartnerMappingDetails(Optional<PartnerMapping> partnerMappingDataOptional,
			Optional<MispLicenseData> mispLicOptional) throws IdAuthenticationBusinessException {
		if (partnerMappingDataOptional.isPresent() && !partnerMappingDataOptional.get().isDeleted()) {
			PartnerMapping partnerMapping = partnerMappingDataOptional.get();
			if (partnerMapping.getPartnerData().isDeleted()) {
				throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.PARTNER_NOT_REGISTERED.getErrorCode(),
						IdAuthenticationErrorConstants.PARTNER_NOT_REGISTERED.getErrorMessage());
			}
			if (!partnerMapping.getPartnerData().getPartnerStatus().contentEquals("ACTIVE")) {
				throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.PARTNER_DEACTIVATED.getErrorCode(),
						IdAuthenticationErrorConstants.PARTNER_DEACTIVATED.getErrorMessage());
			}
			if (partnerMapping.getPolicyData().isDeleted()) {
				throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.INVALID_POLICY_ID.getErrorCode(),
						IdAuthenticationErrorConstants.INVALID_POLICY_ID.getErrorMessage());
			}
			if (!partnerMapping.getPolicyData().getPolicyStatus().contentEquals("ACTIVE")) {
				throw new IdAuthenticationBusinessException(
						IdAuthenticationErrorConstants.PARTNER_POLICY_NOT_ACTIVE.getErrorCode(),
						IdAuthenticationErrorConstants.PARTNER_POLICY_NOT_ACTIVE.getErrorMessage());
			}
			if (partnerMapping.getPolicyData().getPolicyCommenceOn().isAfter(DateUtils.getUTCCurrentDateTime())
					|| partnerMapping.getPolicyData().getPolicyExpiresOn()
							.isBefore(DateUtils.getUTCCurrentDateTime())) {
				throw new IdAuthenticationBusinessException(
						IdAuthenticationErrorConstants.PARTNER_POLICY_NOT_ACTIVE.getErrorCode(),
						IdAuthenticationErrorConstants.PARTNER_POLICY_NOT_ACTIVE.getErrorMessage());
			}
			if (partnerMapping.getApiKeyData().isDeleted()) {
				throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.PARTNER_NOT_REGISTERED.getErrorCode(),
						IdAuthenticationErrorConstants.PARTNER_NOT_REGISTERED.getErrorMessage());
			}
			if (!partnerMapping.getApiKeyData().getApiKeyStatus().contentEquals("ACTIVE")) {
				throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.PARTNER_DEACTIVATED.getErrorCode(),
						IdAuthenticationErrorConstants.PARTNER_DEACTIVATED.getErrorMessage());
			}
			if (partnerMapping.getApiKeyData().getApiKeyCommenceOn().isAfter(DateUtils.getUTCCurrentDateTime())
					|| partnerMapping.getApiKeyData().getApiKeyExpiresOn()
							.isBefore(DateUtils.getUTCCurrentDateTime())) {
				throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.PARTNER_NOT_REGISTERED.getErrorCode(),
						IdAuthenticationErrorConstants.PARTNER_NOT_REGISTERED.getErrorMessage());
			}
			if (mispLicOptional.isPresent()) {
				MispLicenseData mispLicenseData = mispLicOptional.get();
				if (mispLicenseData.isDeleted()) {
					throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.INVALID_LICENSEKEY.getErrorCode(),
							IdAuthenticationErrorConstants.INVALID_LICENSEKEY.getErrorMessage());
				}
				if (!mispLicenseData.getMispStatus().contentEquals("ACTIVE")) {
					throw new IdAuthenticationBusinessException(
							IdAuthenticationErrorConstants.LICENSEKEY_SUSPENDED.getErrorCode(),
							IdAuthenticationErrorConstants.LICENSEKEY_SUSPENDED.getErrorMessage());
				}
				if (mispLicenseData.getMispCommenceOn().isAfter(DateUtils.getUTCCurrentDateTime())) {
					// TODO need to throw different exception for misp not active
					throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.INVALID_LICENSEKEY.getErrorCode(),
							IdAuthenticationErrorConstants.INVALID_LICENSEKEY.getErrorMessage());
				}
				if (mispLicenseData.getMispExpiresOn().isBefore(DateUtils.getUTCCurrentDateTime())) {
					throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.LICENSEKEY_EXPIRED.getErrorCode(),
							IdAuthenticationErrorConstants.LICENSEKEY_EXPIRED.getErrorMessage());
				}
			} else {
				throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.INVALID_LICENSEKEY.getErrorCode(),
						IdAuthenticationErrorConstants.INVALID_LICENSEKEY.getErrorMessage());
			}
		} else {
			throw new IdAuthenticationBusinessException(IdAuthenticationErrorConstants.PARTNER_NOT_REGISTERED.getErrorCode(),
					IdAuthenticationErrorConstants.PARTNER_NOT_REGISTERED.getErrorMessage());
		}
	}

	/**
	 * Handle api key approved.
	 *
	 * @param eventModel the event model
	 * @throws JsonParseException the json parse exception
	 * @throws JsonMappingException the json mapping exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void handleApiKeyApproved(EventModel eventModel) throws JsonParseException, JsonMappingException, IOException {
		PartnerMapping mapping = new PartnerMapping();
		PartnerData partnerEventData = mapper.convertValue(eventModel.getEvent().getData().get(PARTNER_DATA),
				PartnerData.class);
		mapping.setPartnerId(partnerEventData.getPartnerId());
		partnerEventData.setCreatedBy(getCreatedBy(eventModel));
		partnerEventData.setCrDTimes(DateUtils.getUTCCurrentDateTime());
		ApiKeyData apiKeyEventData = mapper.convertValue(eventModel.getEvent().getData().get(API_KEY_DATA),
				ApiKeyData.class);
		mapping.setApiKeyId(apiKeyEventData.getApiKeyId());
		apiKeyEventData.setCreatedBy(getCreatedBy(eventModel));
		apiKeyEventData.setCrDTimes(DateUtils.getUTCCurrentDateTime());
		PolicyData policyEventData = mapper.convertValue(eventModel.getEvent().getData().get(POLICY_DATA),
				PolicyData.class);
		mapping.setPolicyId(policyEventData.getPolicyId());
		policyEventData.setCreatedBy(getCreatedBy(eventModel));
		policyEventData.setCrDTimes(DateUtils.getUTCCurrentDateTime());
		mapping.setCreatedBy(getCreatedBy(eventModel));
		mapping.setCrDTimes(DateUtils.getUTCCurrentDateTime());
		partnerDataRepo.save(partnerEventData);
		apiKeyRepo.save(apiKeyEventData);
		policyDataRepo.save(policyEventData);
		partnerMappingRepo.save(mapping);
	}

	
	/**
	 * Gets the created by.
	 *
	 * @param eventModel the event model
	 * @return the created by
	 */
	private String getCreatedBy(EventModel eventModel) {
		//Get user from session
		String user = securityManager.getUser();
		if (user == null) {
			//Get publisher from event
			String publisher = eventModel.getPublisher();
			if (publisher == null) {
				//return as created by IDA
				return IdAuthCommonConstants.IDA;
			} else {
				return publisher;
			}
		} else {
			return user;
		}
	}
	
	/**
	 * Handle api key updated.
	 *
	 * @param eventModel the event model
	 * @throws JsonParseException the json parse exception
	 * @throws JsonMappingException the json mapping exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void handleApiKeyUpdated(EventModel eventModel)
			throws JsonParseException, JsonMappingException, IOException {
		ApiKeyData apiKeyEventData = mapper.convertValue(eventModel.getEvent().getData().get(API_KEY_DATA),
				ApiKeyData.class);
		Optional<ApiKeyData> apiKeyDataOptional = apiKeyRepo.findById(apiKeyEventData.getApiKeyId());
		if (apiKeyDataOptional.isPresent()) {
			ApiKeyData apiKeyData = apiKeyDataOptional.get();
			apiKeyData.setApiKeyCommenceOn(apiKeyEventData.getApiKeyCommenceOn());
			apiKeyData.setApiKeyExpiresOn(apiKeyEventData.getApiKeyExpiresOn());
			apiKeyData.setApiKeyStatus(apiKeyEventData.getApiKeyStatus());
			apiKeyData.setUpdatedBy(getCreatedBy(eventModel));
			apiKeyData.setUpdDTimes(DateUtils.getUTCCurrentDateTime());
			apiKeyRepo.save(apiKeyData);
		} else {
			apiKeyEventData.setCreatedBy(getCreatedBy(eventModel));
			apiKeyEventData.setCrDTimes(DateUtils.getUTCCurrentDateTime());
			apiKeyRepo.save(apiKeyEventData);
		}
	}

	/**
	 * Update partner data.
	 *
	 * @param eventModel the event model
	 */
	public void updatePartnerData(EventModel eventModel) {
		PartnerData partnerEventData = mapper.convertValue(eventModel.getEvent().getData().get(PARTNER_DATA), PartnerData.class);
		Optional<PartnerData> partnerDataOptional = partnerDataRepo.findById(partnerEventData.getPartnerId());
		if (partnerDataOptional.isPresent()) {
			PartnerData partnerData = partnerDataOptional.get();
			partnerData.setPartnerId(partnerEventData.getPartnerId());
			partnerData.setPartnerName(partnerEventData.getPartnerName());
			partnerData.setCertificateData(partnerEventData.getCertificateData());
			partnerData.setPartnerStatus(partnerEventData.getPartnerStatus());
			partnerData.setUpdatedBy(getCreatedBy(eventModel));
			partnerData.setUpdDTimes(DateUtils.getUTCCurrentDateTime());
			partnerDataRepo.save(partnerData);
		} else {
			partnerEventData.setCreatedBy(getCreatedBy(eventModel));
			partnerEventData.setCrDTimes(DateUtils.getUTCCurrentDateTime());
			partnerDataRepo.save(partnerEventData);
		}
	}

	/**
	 * Update policy data.
	 *
	 * @param eventModel the event model
	 */
	public void updatePolicyData(EventModel eventModel) {
		PolicyData policyEventData = mapper.convertValue(eventModel.getEvent().getData().get(POLICY_DATA), PolicyData.class);
		Optional<PolicyData> policyDataOptional = policyDataRepo.findById(policyEventData.getPolicyId());
		if (policyDataOptional.isPresent()) {
			PolicyData policyData = policyDataOptional.get();
			policyData.setUpdatedBy(getCreatedBy(eventModel));
			policyData.setUpdDTimes(DateUtils.getUTCCurrentDateTime());
			policyData.setPolicyId(policyEventData.getPolicyId());
			policyData.setPolicy(policyEventData.getPolicy());
			policyData.setPolicyName(policyEventData.getPolicyName());
			policyData.setPolicyStatus(policyEventData.getPolicyStatus());
			policyData.setPolicyDescription(policyEventData.getPolicyDescription());
			policyData.setPolicyCommenceOn(policyEventData.getPolicyCommenceOn());
			policyData.setPolicyExpiresOn(policyEventData.getPolicyExpiresOn());
			policyDataRepo.save(policyData);
		} else {
			policyEventData.setCreatedBy(getCreatedBy(eventModel));
			policyEventData.setCrDTimes(DateUtils.getUTCCurrentDateTime());
			policyDataRepo.save(policyEventData);
		}
	}

	/**
	 * Update misp license data.
	 *
	 * @param eventModel the event model
	 */
	public void updateMispLicenseData(EventModel eventModel) {
		MispLicenseData mispLicenseEventData = mapper.convertValue(eventModel.getEvent().getData().get(MISP_LICENSE_DATA), MispLicenseData.class);
		Optional<MispLicenseData> mispLicenseDataOptional = mispLicDataRepo.findById(mispLicenseEventData.getMispId());
		if (mispLicenseDataOptional.isPresent()) {
			MispLicenseData mispLicenseData = mispLicenseDataOptional.get();
			mispLicenseData.setUpdatedBy(getCreatedBy(eventModel));
			mispLicenseData.setUpdDTimes(DateUtils.getUTCCurrentDateTime());
			mispLicenseData.setMispId(mispLicenseEventData.getMispId());
			mispLicenseData.setLicenseKey(mispLicenseEventData.getLicenseKey());
			mispLicenseData.setMispCommenceOn(mispLicenseEventData.getMispCommenceOn());
			mispLicenseData.setMispExpiresOn(mispLicenseEventData.getMispExpiresOn());
			mispLicenseData.setMispStatus(mispLicenseEventData.getMispStatus());
			mispLicDataRepo.save(mispLicenseData);
		} else {
			mispLicenseEventData.setCreatedBy(getCreatedBy(eventModel));
			mispLicenseEventData.setCrDTimes(DateUtils.getUTCCurrentDateTime());
			mispLicDataRepo.save(mispLicenseEventData);
		}
	}
}