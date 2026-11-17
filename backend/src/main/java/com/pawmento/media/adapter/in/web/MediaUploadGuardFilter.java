package com.pawmento.media.adapter.in.web;

import com.pawmento.global.web.ApiPaths;
import com.pawmento.media.application.port.in.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

// Spring Security 뒤, DispatcherServlet의 multipart 파싱 전에 실행한다.
@RequiredArgsConstructor
class MediaUploadGuardFilter extends OncePerRequestFilter {
  private static final PathPattern PATH =
      new PathPatternParser().parse(ApiPaths.V1 + "/pets/{petId}/media/{assetId}/content");
  private final CheckMediaUploadUseCase check;
  private final HandlerExceptionResolver errors;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    var match = PATH.matchAndExtract(PathContainer.parsePath(path));
    if (!"POST".equals(request.getMethod()) || match == null) {
      chain.doFilter(request, response);
      return;
    }
    try {
      if (!"1".equals(request.getHeader("X-Pawmento-Upload")) || !sameOrigin(request))
        throw new MediaUploadException(
            MediaUploadException.Kind.FORBIDDEN, "허용된 화면에서 파일을 업로드해 주세요.");
      if (request.getContentLengthLong() > 16L * 1024 * 1024)
        throw new MediaUploadException(
            MediaUploadException.Kind.TOO_LARGE, "요청은 16MiB까지 보낼 수 있어요.");
      if (request.getUserPrincipal() == null)
        throw new MediaUploadException(MediaUploadException.Kind.FORBIDDEN, "로그인이 필요합니다.");
      check.check(
          new CheckMediaUploadQuery(
              Long.valueOf(request.getUserPrincipal().getName()),
              positiveId(match.getUriVariables().get("petId")),
              positiveId(match.getUriVariables().get("assetId"))));
    } catch (RuntimeException e) {
      if (errors.resolveException(request, response, null, e) == null) throw e;
      return;
    }
    chain.doFilter(request, response);
  }

  private static Long positiveId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException e) {
      throw new MediaUploadException(MediaUploadException.Kind.INVALID, "파일 경로를 확인해 주세요.");
    }
  }

  private static boolean sameOrigin(HttpServletRequest request) {
    String value = request.getHeader("Origin");
    if (value == null) return false;
    try {
      URI uri = URI.create(value);
      if (uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !(uri.getRawPath() == null || uri.getRawPath().isEmpty())
          || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) return false;
      int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
      return uri.getScheme().equals(request.getScheme())
          && uri.getHost().equalsIgnoreCase(request.getServerName())
          && port == request.getServerPort();
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
