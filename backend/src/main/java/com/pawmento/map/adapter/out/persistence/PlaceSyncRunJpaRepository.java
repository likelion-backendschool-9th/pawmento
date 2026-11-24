package com.pawmento.map.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pawmento.map.domain.PlaceCategory;

interface PlaceSyncRunJpaRepository extends JpaRepository<PlaceSyncRunJpaEntity, PlaceCategory> {

}
