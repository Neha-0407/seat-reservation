package com.paytm.seat_reservation.service;

import com.paytm.seat_reservation.dto.CreateSeatCategoryRequest;
import com.paytm.seat_reservation.entity.SeatCategory;
import com.paytm.seat_reservation.repository.ShowAdminRepository;
import com.paytm.seat_reservation.repository.SeatCategoryRepository;
import com.paytm.seat_reservation.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SeatCategoryService {

    private final SeatCategoryRepository seatCategoryRepository;
    private final ShowRepository showRepository;
    private final ShowAdminRepository showAdminRepository;

    public SeatCategoryService(
            SeatCategoryRepository seatCategoryRepository,
            ShowRepository showRepository,
            ShowAdminRepository showAdminRepository
    ) {
        this.seatCategoryRepository = seatCategoryRepository;
        this.showRepository = showRepository;
        this.showAdminRepository = showAdminRepository;
    }

    @Transactional
    public SeatCategory createCategory(UUID showId, String userId, CreateSeatCategoryRequest request) {
        showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
        requireShowAdmin(showId, userId);

        if (seatCategoryRepository.findByShowIdAndCategoryName(showId, request.getCategoryName()).isPresent()) {
            throw new IllegalArgumentException("Category already exists for show: " + request.getCategoryName());
        }

        SeatCategory category = new SeatCategory();
        category.setId(UUID.randomUUID());
        category.setShowId(showId);
        category.setCategoryName(request.getCategoryName());
        category.setPricePaise(request.getPricePaise());
        return seatCategoryRepository.save(category);
    }

    public List<SeatCategory> getCategories(UUID showId) {
        showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
        return seatCategoryRepository.findByShowId(showId);
    }

    private void requireShowAdmin(UUID showId, String userId) {
        if (!showAdminRepository.existsByShowIdAndUserId(showId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User is not an admin of show: " + showId);
        }
    }
}