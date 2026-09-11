package com.example.gitbot.repository;

import com.example.gitbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGithubId(Long githubId);


}
