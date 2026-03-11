package com.bdmage.mage_backend.repository;

import java.util.Optional;

import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider);

	Optional<User> findByGoogleSubject(String googleSubject);
}
