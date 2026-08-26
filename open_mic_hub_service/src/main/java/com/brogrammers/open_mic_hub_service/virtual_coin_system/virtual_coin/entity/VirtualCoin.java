package com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity;

import com.brogrammers.open_mic_hub_service.common.Auditable;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class VirtualCoin extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long virtualCoinId;
    private Double balance;

    @OneToOne
    private Artist artist;
}
