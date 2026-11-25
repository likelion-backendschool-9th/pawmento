package com.pawmento.map.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

/**
 * 공공데이터를 믿고 반영할 수 있게 받지 못해 가져오기를 중단했다. 이 예외가 나면 저장된 장소는 하나도 바뀌지 않는다(#172-S5).
 * 다시 시도하면 성공할 수 있어서 일시 장애(503)로 응답한다. 인증키와 요청 주소가 새지 않도록 메시지는 고정 문구만 쓰고,
 * 왜 중단했는지는 {@link #reason()}으로 알린다(로그와 테스트용).
 */
public class PublicDataSyncFailedException extends StorageUnavailableException {

	public enum Reason {

		// 상태·좌표로 거르기 전의 고유 관리번호 수가 totalCount와 다르다(페이지가 겹쳐 다른 관리번호가 빠진 경우 포함)
		TOTAL_COUNT_MISMATCH,

		// 수집하는 동안 totalCount가 바뀌었다
		TOTAL_COUNT_CHANGED,

		// 필수 항목이 빠진 행이 있다
		MISSING_FIELDS,

		// 거른 뒤 저장할 건수가 기존의 절반 미만이다
		TOO_FEW_PLACES,

		// 중간 페이지 호출이 실패하거나 시간 초과했다
		FETCH_FAILED,

		// 받은 행이 하나도 없다(보호센터). 서비스 오류일 수 있어 지도를 비우지 않는다
		EMPTY_SNAPSHOT,

		// 받은 건수가 요청한 수와 같거나 더 많아 뒤에 더 있을 수 있다(보호센터)
		POSSIBLY_TRUNCATED,

		// 같은 등록번호가 둘 이상이다(보호센터)
		DUPLICATE_IDS,

		// 포털이 요청을 거부했다(인증키 없음·미등록·기간 만료, 접근 거부, 호출 한도 초과, 잘못된 요청). 다시 시도해도 같다
		FETCH_REJECTED

	}

	private static final String MESSAGE = "공공데이터를 완전하게 받지 못해 가져오기를 중단했습니다. 저장된 장소는 바뀌지 않았습니다. 잠시 뒤 다시 시도해 주세요.";

	private final Reason reason;

	private PublicDataSyncFailedException(Reason reason, Throwable cause) {
		super(MESSAGE, cause);
		this.reason = reason;
	}

	public static PublicDataSyncFailedException rejected(Reason reason) {
		return new PublicDataSyncFailedException(reason, null);
	}

	// 호출 실패의 원인은 보존하되, 원인 메시지에 요청 주소(인증키 포함)가 들어 있지 않게 어댑터가 정리해서 넘긴다
	public static PublicDataSyncFailedException fetchFailed(Throwable cause) {
		return new PublicDataSyncFailedException(Reason.FETCH_FAILED, cause);
	}

	// 포털이 요청 자체를 거부한 경우다. 호출 실패와 달리 다시 시도해도 같아서 FETCH_REJECTED로 구분한다
	public static PublicDataSyncFailedException fetchRejected(Throwable cause) {
		return new PublicDataSyncFailedException(Reason.FETCH_REJECTED, cause);
	}

	public Reason reason() {
		return reason;
	}

	/**
	 * 시간을 두고 다시 하면 성공할 수 있는 실패인가. 필수 항목이 빠진 응답(원본 형식이 바뀜), 포털이 거부한 요청(키·권한·한도),
	 * 받은 건수가 요청한 수 이상(더 있을 수 있음)이거나 등록번호가 겹치는 보호센터 응답은 다시 해도 같은 결과라서 자동으로 다시
	 * 시도하지 않고, 사람이 고쳐야 한다.
	 */
	public boolean retryable() {
		return reason != Reason.MISSING_FIELDS && reason != Reason.FETCH_REJECTED && reason != Reason.POSSIBLY_TRUNCATED
				&& reason != Reason.DUPLICATE_IDS;
	}

}
