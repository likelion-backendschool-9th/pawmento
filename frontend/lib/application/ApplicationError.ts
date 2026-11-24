// UI가 HTTP 상태나 전송 구현을 몰라도 처리할 수 있는 애플리케이션 오류다.
export class ApplicationError extends Error {
  readonly code: string;
  constructor(code: string, message: string) {
    super(message);
    this.name = "ApplicationError";
    this.code = code;
  }
}
