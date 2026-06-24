package com.example.vote.modal.user;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Data
@Table(name = "users")
public class User {

    // ===== ENUMS =====

    public enum Gender {
        MALE, FEMALE
    }

    public enum ActivityLevel {
        SEDENTARY, LIGHTLY_ACTIVE, MODERATELY_ACTIVE, VERY_ACTIVE
    }

    public enum SleepQuality {
        POOR, FAIR, GOOD, EXCELLENT
    }

    public enum DailyStructure {
        STUDENT,
        WORKING_9TO5,
        SHIFT_WORKER,
        FREELANCER,
        PARENT,
        RETIRED,
        UNEMPLOYED,
        OTHER
    }

    public enum EnergyPeak {
        MORNING,
        AFTERNOON,
        EVENING,
        NIGHT
    }

    public enum WeekendAvailability {
        FULLY_FREE,
        PARTIALLY_FREE,
        BUSY,
        VARIABLE
    }

    public enum Occupations {
        ACCOUNTANT,
        ADMINISTRATIVE_ASSISTANT,
        BANK_TELLER,
        BARISTA,
        BARTENDER,
        CARPENTER,
        CASHIER,
        CHEF,
        CIVIL_ENGINEER,
        COMPUTER_PROGRAMMER,
        CONSTRUCTION_LABORER,
        CONTENT_CREATOR,
        COUNSELOR,
        COURT_REPORTER,
        CYBERSECURITY_ANALYST,
        DATA_ENTRY_CLERK,
        DATA_SCIENTIST,
        DENTIST,
        DOCTOR,
        EDITOR,
        ELECTRICAL_ENGINEER,
        ELECTRICIAN,
        FINANCIAL_ANALYST,
        FLIGHT_ATTENDANT,
        FOOD_SERVICE_WORKER,
        GOVERNMENT_ADMINISTRATOR,
        GRAPHIC_DESIGNER,
        HOTEL_FRONT_DESK,
        HR_SPECIALIST,
        HVAC_TECHNICIAN,
        IT_SUPPORT,
        JANITOR,
        JOURNALIST,
        LANDSCAPER,
        LAWYER,
        LIBRARIAN,
        LOAN_OFFICER,
        MANAGER,
        MARKETING_SPECIALIST,
        MECHANIC,
        MEDICAL_ASSISTANT,
        MUSICIAN,
        NURSE,
        PAINTER,
        PARALEGAL,
        PHARMACIST,
        PHOTOGRAPHER,
        PHYSICAL_THERAPIST,
        PLUMBER,
        POLICE_OFFICER,
        POSTAL_WORKER,
        PROFESSOR,
        PUBLIC_RELATIONS_SPECIALIST,
        RADIOLOGIC_TECHNOLOGIST,
        REAL_ESTATE_AGENT,
        RECEPTIONIST,
        RETAIL_SALES_ASSOCIATE,
        SALES_REPRESENTATIVE,
        SECRETARY,
        SERVER,
        SOCIAL_WORKER,
        SOFTWARE_DEVELOPER,
        SPECIAL_EDUCATION_TEACHER,
        SURGEON,
        TEACHER,
        TEACHER_AIDE,
        TRUCK_DRIVER,
        UX_UI_DESIGNER,
        VETERINARIAN,
        WAREHOUSE_WORKER,
        WELDER,
        WRITER
    }

    // ===== FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Authentication
    @Column(unique = true, nullable = false)
    private String email;

    private String userName;

    private String role;

    private LocalDateTime createdDate;

    // Demographics
    private Integer age; // Use Integer to allow null

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition  = "gender_enum")
    private Gender gender;

    private Integer height; // in cm
    private Integer weight; // in kg

    // Schedule
    private LocalTime sleepTime;
    private LocalTime wakeTime;

    // Lifestyle
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "activity_level", columnDefinition  = "activity_level")
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition  = "occupation_enum")
    private Occupations occupation;

    // ===== NEW FIELDS (CRITICAL ADDITIONS) =====

    /**
     * What best describes their typical day structure.
     * This is the SINGLE most important field for mission timing.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition  = "daily_structure_enum")
    private DailyStructure dailyStructure;

    /**
     * Custom text if DailyStructure = OTHER
     */
    private String dailyStructureCustom;

    /**
     * When the user is typically UNAVAILABLE (e.g., "9 AM - 5 PM", "Classes 8 AM - 3 PM")
     * This prevents scheduling missions during blocked time.
     */
    private String blockedTime;

    /**
     * When the user is AVAILABLE for missions (e.g., "7-8 AM, 6-10 PM")
     * This is where missions will be scheduled.
     */
    private String freeTimeWindows;

    /**
     * When the user has the most energy (morning, afternoon, evening, night).
     * HARD missions should be scheduled here.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition  = "energy_peak_enum")
    private EnergyPeak energyPeak;

    /**
     * How weekends differ from weekdays.
     * Helps adjust mission difficulty and timing.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition  = "weekend_availability_enum")
    private WeekendAvailability weekendAvailability;

    /**
     * The user's main obstacle (extracted from interview).
     * Example: "Stress eating at my desk at 3 PM"
     */
    @Column(length = 1000)
    private String mainObstacle;

    /**
     * Environmental factors that help or hinder.
     * Example: "My kitchen is full of junk food"
     */
    @Column(length = 1000)
    private String environmentNotes;

    /**
     * LLM-generated markdown profile.
     * This is the synthesized, structured profile used for mission generation.
     */
    @Column(columnDefinition = "TEXT")
    private String llmProfileMarkdown;

    /**
     * When the LLM profile was last generated/updated.
     */
    private LocalDateTime llmProfileUpdatedAt;

    /**
     * Whether the user profile is complete enough for mission generation.
     */
    private boolean profileComplete = false;

    /**
     * When the user last completed a mission (for streak tracking).
     */
    private LocalDateTime lastMissionCompletedAt;

    /**
     * Current mission streak in days.
     */
    private Integer missionStreakDays = 0;

    // ===== HELPER METHODS =====

    /**
     * Check if the user profile is stale and needs regeneration.
     * Returns true if profile is null or older than X days.
     */
    public boolean isProfileStale() {
        if (llmProfileMarkdown == null || llmProfileMarkdown.isEmpty()) {
            return true;
        }
        if (llmProfileUpdatedAt == null) {
            return true;
        }
        return llmProfileUpdatedAt.isBefore(LocalDateTime.now().minusDays(30));
    }

    /**
     * Check if the user has provided enough schedule data.
     */
    public boolean hasScheduleData() {
        return freeTimeWindows != null && !freeTimeWindows.isEmpty()
                && blockedTime != null && !blockedTime.isEmpty()
                && energyPeak != null;
    }

    /**
     * Check if the user has provided obstacle/environment data.
     */
    public boolean hasObstacleData() {
        return mainObstacle != null && !mainObstacle.isEmpty()
                && environmentNotes != null && !environmentNotes.isEmpty();
    }

    /**
     * Determine if profile is complete for mission generation.
     */
    public boolean isProfileReadyForMissions() {
        return hasScheduleData() && hasObstacleData() && dailyStructure != null;
    }

}
