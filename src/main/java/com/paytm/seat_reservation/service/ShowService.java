package com.paytm.seat_reservation.service;

import com.paytm.seat_reservation.dto.CreateShowRequest;
import com.paytm.seat_reservation.entity.Show;
import com.paytm.seat_reservation.entity.ShowAdmin;
import com.paytm.seat_reservation.repository.ShowAdminRepository;
import com.paytm.seat_reservation.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowAdminRepository showAdminRepository;

    public ShowService(ShowRepository showRepository, ShowAdminRepository showAdminRepository) {
        this.showRepository = showRepository;
        this.showAdminRepository = showAdminRepository;
    }

    @Transactional
    public Show createShow(String creatorUserId, CreateShowRequest request) {
        Show show = new Show();
        show.setId(UUID.randomUUID());
        show.setName(request.getName());
        show.setPricePaise(request.getPricePaise());
        show.setTotalSeats(request.getTotalSeats());
        show.setPerUserLimit(request.getPerUserLimit());
        show.setCreatedAt(OffsetDateTime.now());
        show = showRepository.save(show);

        ShowAdmin showAdmin = new ShowAdmin();
        showAdmin.setId(UUID.randomUUID());
        showAdmin.setShowId(show.getId());
        showAdmin.setUserId(creatorUserId);
        showAdmin.setCreatedAt(OffsetDateTime.now());
        showAdminRepository.save(showAdmin);
        return show;
    }

    public Show getShow(UUID showId) {
        return showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
    }
}
