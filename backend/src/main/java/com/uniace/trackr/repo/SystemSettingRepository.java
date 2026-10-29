package com.uniace.trackr.repo;

import com.uniace.trackr.domain.SystemSetting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    /**
     * All settings, with the account that last changed each one already loaded -
     * the settings screen shows that name on every row, and the rows are mapped
     * outside the loading transaction.
     */
    @Query("""
            select s from SystemSetting s
              left join fetch s.updatedBy
            order by s.key asc
            """)
    List<SystemSetting> findAllByOrderByKeyAsc();
}
