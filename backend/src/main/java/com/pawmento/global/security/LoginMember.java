package com.pawmento.global.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드의 Long 파라미터에 붙이면 지금 로그인한 회원의 id가 들어온다.
 *
 * <pre>
 * &#64;GetMapping("/pets")
 * List&lt;PetResponse&gt; myPets(&#64;LoginMember Long memberId) {
 *     return petUseCase.findByOwner(memberId);
 * }
 * </pre>
 *
 * 실제 경로는 GET /api/v1/pets다. /api/v1은 WebConfig가 붙인다.
 * 서비스(애플리케이션 계층)에는 이 id만 넘긴다. 서비스가 Spring Security를 직접 쓰지 않게 한다.
 * 로그인이 필요한 경로에서만 쓴다. 로그인하지 않은 요청이면 401로 응답한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginMember {
}
