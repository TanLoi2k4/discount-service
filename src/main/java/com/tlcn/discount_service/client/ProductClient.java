package com.tlcn.discount_service.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.tlcn.discount_service.dto.ProductSummary;
import com.tlcn.discount_service.dto.ResponseDTO;
import com.tlcn.discount_service.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductClient {

    private final RestTemplate restTemplate;

    public ProductSummary getProduct(Long productId) {
        try {
            ResponseEntity<ResponseDTO<ProductSummary>> response = restTemplate.exchange(
                "http://product-service/products/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ResponseDTO<ProductSummary>>() {}
            );

            ResponseDTO<ProductSummary> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new ResourceNotFoundException("Product not found: " + productId);
            }

            return body.getData();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        } catch (HttpClientErrorException e) {
            log.warn("Failed to fetch product {}: HTTP {}", productId, e.getStatusCode());
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }
}
