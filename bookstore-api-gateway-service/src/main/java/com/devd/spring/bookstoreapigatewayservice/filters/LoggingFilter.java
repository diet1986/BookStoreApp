package com.devd.spring.bookstoreapigatewayservice.filters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Logging filter for Spring Cloud Gateway MVC.
 * Replaces the old Zuul PreFilter / PostFilter / RouteFilter.
 *
 * HandlerFilterFunction<ServerResponse, ServerResponse> requires implementing:
 * filter(ServerRequest, HandlerFunction<ServerResponse>)
 */
@Slf4j
@Component
public class LoggingFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Override
    public ServerResponse filter(ServerRequest request,
                                 HandlerFunction<ServerResponse> next) throws Exception {
        log.info("Gateway request: {} {}", request.method(), request.uri());
        ServerResponse response = next.handle(request);
        log.info("Gateway response: {} for {} {}", response.statusCode(), request.method(), request.uri());
        return response;
    }
}
