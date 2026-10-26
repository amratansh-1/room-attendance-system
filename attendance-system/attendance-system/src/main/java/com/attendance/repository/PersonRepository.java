package com.attendance.repository;

import com.attendance.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

    boolean existsByPersonCode(String personCode);
}
