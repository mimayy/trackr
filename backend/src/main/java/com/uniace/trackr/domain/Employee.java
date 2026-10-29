package com.uniace.trackr.domain;

import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.WorkCategory;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee profile.
 *
 * <p>{@code workCategory} describes how the person is employed, not where they
 * happen to be today. A FIELD_BASED employee who spends Tuesday at the office
 * stays FIELD_BASED.
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human readable staff number, e.g. {@code EMP-001}. */
    @Column(name = "employee_no", nullable = false, unique = true, length = 40)
    private String employeeNo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "contact_number", length = 40)
    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_category", nullable = false, length = 20)
    private WorkCategory workCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private Supervisor supervisor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_schedule_id")
    private WorkSchedule workSchedule;

    /** The office this employee normally reports to. Defaults from the branch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_office_id")
    private WorkLocation homeOffice;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private AccountStatus accountStatus = AccountStatus.PENDING_ACTIVATION;

    @Column(name = "hired_on")
    private LocalDate hiredOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim().toLowerCase(); }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public WorkCategory getWorkCategory() { return workCategory; }
    public void setWorkCategory(WorkCategory workCategory) { this.workCategory = workCategory; }

    public Supervisor getSupervisor() { return supervisor; }
    public void setSupervisor(Supervisor supervisor) { this.supervisor = supervisor; }

    public WorkSchedule getWorkSchedule() { return workSchedule; }
    public void setWorkSchedule(WorkSchedule workSchedule) { this.workSchedule = workSchedule; }

    public WorkLocation getHomeOffice() { return homeOffice; }
    public void setHomeOffice(WorkLocation homeOffice) { this.homeOffice = homeOffice; }

    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }

    public LocalDate getHiredOn() { return hiredOn; }
    public void setHiredOn(LocalDate hiredOn) { this.hiredOn = hiredOn; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
