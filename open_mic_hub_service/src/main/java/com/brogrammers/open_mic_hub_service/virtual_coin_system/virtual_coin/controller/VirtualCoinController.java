package com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto.VirtualCoinRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.service.VirtualCoinService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/virtual-coins")
public class VirtualCoinController extends BaseController {
    private final VirtualCoinService virtualCoinService;

    @PostMapping("/create")
    public ResponseEntity<GlobalApiResponse> createOrUpdateVirtualCoin(VirtualCoinRequest virtualCoinRequest) {
        return successResponse(virtualCoinService.createOrUpdateVirtualCoin(virtualCoinRequest), "Virtual Coin created or updated successfully");
    }

    @GetMapping("/get-by-artist-id/{artistId}")
    public ResponseEntity<GlobalApiResponse> getVirtualCoinByArtistId(@PathVariable  Long artistId) {
        return successResponse(virtualCoinService.getVirtualCoinByArtistId(artistId), "Virtual Coin fetched successfully");
    }

    @GetMapping("/{virtualCoinId}")
    public ResponseEntity<GlobalApiResponse> getVirtualCoinById(@PathVariable Long virtualCoinId) {
        return successResponse(virtualCoinService.getVirtualCoinById(virtualCoinId), "Virtual Coin fetched successfully");
    }

    @GetMapping("/get-all")
    public ResponseEntity<GlobalApiResponse> getAllVirtualCoins(Pageable pageable) {
        return successResponse(virtualCoinService.getAllVirtualCoins(pageable), "All Virtual Coins fetched successfully");
    }

    @GetMapping("/get-logged-in-artist")
    public ResponseEntity<GlobalApiResponse> getLoggedInArtistVirtualCoin() {
        return successResponse(virtualCoinService.getLoggedInArtistVirtualCoin(), "Logged in artist's Virtual Coin fetched successfully");
    }
}
