package com.pawmento.map.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

/**
 * 공공데이터를 믿고 반영할 수 있게 받지 못해 가져오기를 중단했다. 이 예외가 나면 저장된 장소는 하나도 바뀌지 않는다(#172-S5).
 * 다시 시도하면 성공할 수 있어서 일시 장애(503)로 응답한다. 인증키와 요청 주소가 새지 않도록 메시지는 고정 문구만 쓰고,
 * 왜 중단했는지는 {@link #reason()}으로 알린다(로그와 테스트용).
 */
public class LocalDataSyncFailedException extends StorageUnavailableException {

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
		FETCH_FAILED

	}

	private static final String MESSAGE = "공공데이터를 완전하게 받지 못해 가져오기를 중단했습니다. 저장된 장소는 바뀌지 않았습니다. 잠시 뒤 다시 시도해 주세요.";

	private final Reason reason;

	private LocalDataSyncFailedException(Reason reason, Throwable cause) {
		super(MESSAGE, cause);
		this.reason = reason;
	}

	public static LocalDataSyncFailedException rejected(Reason reason) {
		return new LocalDataSyncFailedException(reason, null);
	}

	// 호출 실패의 원인은 보존하되, 원인 메시지에 요청 주소(인증키 포함)가 들어 있지 않게 어댑터가 정리해서 넘긴다
	public static LocalDataSyncFailedException fetchFailed(Throwable cause) {
		return new LocalDataSyncFailedException(Reason.FETCH_FAILED, cause);
	}

	public Reason reason() {
		return reason;
	}

}
