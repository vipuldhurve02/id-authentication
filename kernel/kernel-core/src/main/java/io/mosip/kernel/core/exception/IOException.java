package io.mosip.kernel.core.exception;

/**
 * @author Priya Soni
 * @author Sidhant Agarwal
 *
 */
public class IOException extends BaseCheckedException {

	private static final long serialVersionUID = 7464354823823721387L;


	public IOException(String errorCode, String errorMessage, Throwable rootCause) {
		super(errorCode, errorMessage, rootCause);

	}


	public IOException(String errorCode, String errorMessage) {
		super(errorCode, errorMessage);

	}

}
