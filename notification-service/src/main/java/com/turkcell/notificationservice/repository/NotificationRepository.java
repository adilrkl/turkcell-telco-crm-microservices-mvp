package com.turkcell.notificationservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.notificationservice.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
