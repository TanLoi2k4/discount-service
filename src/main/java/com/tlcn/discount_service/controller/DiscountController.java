package com.tlcn.discount_service.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.tlcn.discount_service.dto.DiscountPreviewRequest;
import com.tlcn.discount_service.dto.DiscountPreviewResponse;
import com.tlcn.discount_service.dto.DiscountRequest;
import com.tlcn.discount_service.dto.ResponseDTO;
import com.tlcn.discount_service.model.Discount;
import com.tlcn.discount_service.service.DiscountPreviewService;
import com.tlcn.discount_service.service.DiscountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/discounts")
@RequiredArgsConstructor
@Validated
public class DiscountController {

    private final DiscountService discountAdminService;
    private final DiscountPreviewService discountPreviewService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Discount>> create(@Valid @RequestBody DiscountRequest request) {
        Discount saved = discountAdminService.create(request);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Discount created", saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Discount>> update(@PathVariable Long id, @Valid @RequestBody DiscountRequest request) {
        Discount updated = discountAdminService.update(id, request);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Discount updated", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Void>> delete(@PathVariable Long id) {
        discountAdminService.delete(id);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Discount deleted", null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Discount>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseDTO<>(true, "OK", discountAdminService.getById(id)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'CUSTOMER')")
    public ResponseEntity<ResponseDTO<Discount>> getByCode(@PathVariable String code, Authentication authentication) {
        Discount discount = isAdmin(authentication)
                ? discountAdminService.getByCode(code)
                : discountAdminService.getActiveByCode(code);

        return ResponseEntity.ok(new ResponseDTO<>(true, "OK", discount));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'CUSTOMER')")
    public ResponseEntity<ResponseDTO<Page<Discount>>> list(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {

        Page<Discount> result = isAdmin(authentication)
                ? discountAdminService.list(pageable)
                : discountAdminService.listActive(pageable);

        return ResponseEntity.ok(new ResponseDTO<>(true, "OK", result));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<DiscountPreviewResponse>> preview(
            @Valid @RequestBody DiscountPreviewRequest request) {
        DiscountPreviewResponse result = discountPreviewService.preview(request.getCode(), request.getSubtotal());
        String message = result.isValid() ? "Discount preview" : result.getMessage();
        return ResponseEntity.ok(new ResponseDTO<>(true, message, result));
    }

    @GetMapping("/expired")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Page<Discount>>> listExpired(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(new ResponseDTO<>(true, "OK", discountAdminService.listExpired(pageable)));
    }

    private static boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }
}
