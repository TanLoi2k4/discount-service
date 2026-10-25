package com.tlcn.discount_service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.tlcn.discount_service.dto.FlashSaleRequest;
import com.tlcn.discount_service.dto.ResponseDTO;
import com.tlcn.discount_service.model.FlashSale;
import com.tlcn.discount_service.service.FlashSaleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/flash-sales")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('VENDOR')")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @PostMapping
    public ResponseEntity<ResponseDTO<FlashSale>> create(@Valid @RequestBody FlashSaleRequest request,
                                                        Authentication authentication) {
        String keycloakId = authentication != null ? authentication.getName() : null;
        FlashSale saved = flashSaleService.createFlashSale(request, keycloakId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Flash sale created", saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<FlashSale>> getById(@PathVariable Long id,
                                                         Authentication authentication) {
        String keycloakId = authentication != null ? authentication.getName() : null;
        FlashSale flashSale = flashSaleService.getByIdForVendor(id, keycloakId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "OK", flashSale));
    }

    @GetMapping
    public ResponseEntity<ResponseDTO<List<FlashSale>>> listMine(Authentication authentication) {
        String keycloakId = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(new ResponseDTO<>(true, "OK", flashSaleService.listForVendor(keycloakId)));
    }

    @GetMapping("/active")
    public ResponseEntity<ResponseDTO<List<FlashSale>>> listActiveMine(Authentication authentication) {
        String keycloakId = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(new ResponseDTO<>(true, "OK", flashSaleService.getActiveForVendor(keycloakId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> delete(@PathVariable Long id,
                                                    Authentication authentication) {
        String keycloakId = authentication != null ? authentication.getName() : null;
        flashSaleService.delete(id, keycloakId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Flash sale deleted", null));
    }
}
