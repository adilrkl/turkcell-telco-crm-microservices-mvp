package com.turkcell.notificationservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.notificationservice.entity.NotificationPreference;
import com.turkcell.notificationservice.repository.NotificationPreferenceRepository;

/**
 * Iletisim tercihi (opt-in/opt-out) yonetimi (FR-30). Tercih satiri yoksa varsayilan
 * opt-in (izinli). {@link NotificationDispatcher} gonderim oncesi buraya sorar.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    /** Kullanici bu kanaldan bildirim almaya izinli mi? Tercih yoksa varsayilan izinli. */
    @Transactional(readOnly = true)
    public boolean isAllowed(UUID userId, String channel) {
        return repository.findByUserIdAndChannel(userId, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }

    /** Kanal tercihi ekler/gunceller (upsert). */
    @Transactional
    public NotificationPreference setPreference(UUID userId, String channel, boolean enabled) {
        NotificationPreference pref = repository.findByUserIdAndChannel(userId, channel)
                .orElseGet(() -> {
                    NotificationPreference p = new NotificationPreference();
                    p.setUserId(userId);
                    p.setChannel(channel);
                    return p;
                });
        pref.setEnabled(enabled);
        pref.setUpdatedAt(Instant.now());
        return repository.save(pref);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreference> getPreferences(UUID userId) {
        return repository.findByUserId(userId);
    }
}
