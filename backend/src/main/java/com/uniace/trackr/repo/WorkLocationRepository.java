package com.uniace.trackr.repo;

import com.uniace.trackr.domain.WorkLocation;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.LocationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkLocationRepository extends JpaRepository<WorkLocation, UUID> {

    List<WorkLocation> findByActiveTrueOrderByTypeAscNameAsc();

    List<WorkLocation> findByTypeAndActiveTrue(LocationType type);

    List<WorkLocation> findByTypeAndBranchAndActiveTrue(LocationType type, Branch branch);

    Optional<WorkLocation> findByNameIgnoreCase(String name);

    List<WorkLocation> findAllByOrderByTypeAscNameAsc();
}
