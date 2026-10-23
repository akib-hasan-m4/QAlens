package com.qalens.repository;

import com.qalens.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByName(String name);
}
