package com.broker.clientService.Infrastructure.config;

import feign.RequestInterceptor;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class FeignConfig implements RequestInterceptor{

    @Override//intercepts toutes requet feign  et copie automatiquement le header Authorization recu dans
    //la requete entrante vers la requete sortante
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            // Copy headers from the original request to the Feign request
            request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                template.header(headerName, request.getHeader(headerName));
            });
        }
    }

}
