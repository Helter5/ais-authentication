---
name: spring-boot-practices
description: Spring Boot domain-driven architecture and coding conventions (package structure, value objects, entities, repositories, services, REST layer, exception handling) with concrete examples from a modular monolith project. Use when writing or reviewing Spring Boot Java code - entities, services, controllers, repositories, DTOs, exception handling.
---

# Spring Boot Skills

This document outlines recommended Spring Boot development practices with concrete examples from the `meetup4j-modulith-tomato` project.

---

## 1. Package Structure for Code Organization

Follow a **domain-driven, modular architecture** where packages are organized by **business modules** rather than technical layers.

### Recommended Structure

```
dev.sivalabs.meetup4j/
├── shared/                          # Cross-cutting concerns
│   ├── BaseEntity.java
│   ├── DomainException.java
│   ├── ResourceNotFoundException.java
│   └── SpringEventPublisher.java
│
├── events/                          # Events module (bounded context)
│   ├── domain/                      # Domain logic
│   │   ├── models/                  # ViewModels for read operations
│   │   │   └── EventVM.java
│   │   ├── vo/                      # Value Objects
│   │   │   ├── EventId.java
│   │   │   ├── EventCode.java
│   │   │   ├── EventDetails.java
│   │   │   ├── Schedule.java
│   │   │   ├── TicketPrice.java
│   │   │   ├── Capacity.java
│   │   │   └── EventLocation.java
│   │   ├── events/                  # Domain events
│   │   │   ├── EventCreated.java
│   │   │   ├── EventPublished.java
│   │   │   └── EventCancelled.java
│   │   ├── EventEntity.java         # Aggregate root
│   │   ├── EventRepository.java     # Repository interface
│   │   ├── EventService.java        # Write operations
│   │   ├── EventQueryService.java   # Read operations
│   │   ├── EventMapper.java         # Domain to ViewModel mapper
│   │   ├── CreateEventCmd.java      # Command
│   │   ├── PublishEventCmd.java
│   │   └── InvalidEventCreationException.java
│   ├── rest/                        # REST API layer
│   │   ├── converters/              # Type converters
│   │   │   └── StringToEventCodeConverter.java
│   │   ├── EventsController.java
│   │   ├── CreateEventRequest.java  # HTTP Request DTO
│   │   └── CreateEventResponse.java # HTTP Response DTO
│   └── EventsAPI.java               # Module's public API (facade)
│
├── registrations/                   # Registrations module
│   ├── domain/
│   │   ├── vo/
│   │   │   ├── RegistrationId.java
│   │   │   ├── RegistrationCode.java
│   │   │   └── Email.java
│   │   ├── EventRegistrationEntity.java
│   │   ├── RegistrationRepository.java
│   │   ├── EventRegistrationService.java
│   │   ├── EventRegistrationQueryService.java
│   │   └── RegisterAttendeeCmd.java
│   └── rest/
│       ├── converters/
│       ├── EventRegistrationController.java
│       └── EventRegistrationRequest.java
│
└── config/
    └── GlobalExceptionHandler.java
```

### Naming Conventions

| Type                  | Convention           | Example                                                       |
|-----------------------|----------------------|---------------------------------------------------------------|
| **Entities**          | `*Entity`            | `EventEntity`, `EventRegistrationEntity`                      |
| **Value Objects**     | Domain name (record) | `Email`, `EventCode`, `EventId`                               |
| **Commands**          | `*Cmd`               | `CreateEventCmd`, `PublishEventCmd`                           |
| **ViewModels**        | `*VM`                | `EventVM`, `RegistrationVM`                                   |
| **HTTP Request**      | `*Request`           | `CreateEventRequest`, `EventRegistrationRequest`              |
| **HTTP Response**     | `*Response`          | `CreateEventResponse`, `EventRegistrationResponse`            |
| **Repositories**      | `*Repository`        | `EventRepository`, `RegistrationRepository`                   |
| **Services (Write)**  | `*Service`           | `EventService`, `EventRegistrationService`                    |
| **Services (Read)**   | `*QueryService`      | `EventQueryService`, `EventRegistrationQueryService`          |
| **Domain Exceptions** | `*Exception`         | `InvalidEventCreationException`, `EventCancellationException` |
| **Module API**        | `*API`               | `EventsAPI`                                                   |

---

## 2. Value Objects

Value Objects represent domain concepts as **immutable records** with built-in validations. They ensure that invalid data cannot exist in your domain model.

### Key Principles

- Use Java **records** for immutability
- Validate in the **compact constructor**
- Use **Jackson annotations** for JSON marshalling/unmarshalling
- Throw `IllegalArgumentException` for invalid data

### Example 1: Email Value Object with Regex Validation

**File:** `registrations/domain/vo/Email.java`

```java
package dev.sivalabs.meetup4j.registrations.domain.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(@JsonValue String value) {
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @JsonCreator
    public Email {
        AssertUtil.requireNotNull(value, "Email cannot be null");
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
```

**Usage in JSON:**
```json
{
  "attendeeEmail": "user@example.com"
}
```

Jackson automatically deserializes the string `"user@example.com"` to `Email` using `@JsonCreator` and serializes back using `@JsonValue`.

### Example 2: EventCode with TSID Generation

**File:** `events/domain/vo/EventCode.java`

```java
package dev.sivalabs.meetup4j.events.domain.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.sivalabs.meetup4j.shared.TSIDUtil;

public record EventCode(@JsonValue String code) {
    @JsonCreator
    public EventCode {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Event code cannot be null");
        }
    }

    public static EventCode of(String code) {
        return new EventCode(code);
    }

    public static EventCode generate() {
        return new EventCode(TSIDUtil.generateTsidString());
    }
}
```

**Benefits:**
- `@JsonValue`: Serializes as a plain string instead of `{"code": "ABC123"}`
- `@JsonCreator`: Deserializes from a plain string
- Factory methods for creation and generation

### Example 3: EventDetails with Jakarta Validation

**File:** `events/domain/vo/EventDetails.java`

```java
package dev.sivalabs.meetup4j.events.domain.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static dev.sivalabs.meetup4j.shared.AssertUtil.requireNotNull;

public record EventDetails(
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 10000, message = "Description cannot exceed 10000 characters")
        String description,

        @Size(max = 500, message = "Image URL cannot exceed 500 characters")
        @Pattern(regexp = "^https?://.*", message = "Image URL must be a valid HTTP/HTTPS URL")
        String imageUrl) {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public EventDetails(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("imageUrl") String imageUrl
    ) {
        this.title = requireNotNull(title, "title cannot be null");
        this.description = requireNotNull(description, "description cannot be null");
        this.imageUrl = imageUrl;
    }

    public static EventDetails of(String title, String description, String imageUrl) {
        return new EventDetails(title, description, imageUrl);
    }
}
```

**Key Features:**
- Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, `@Pattern`)
- Explicit `@JsonCreator` with `@JsonProperty` for property-based deserialization
- Factory method for convenience

---

## 3. Entities

Entities are **aggregate roots** with rich domain logic. Use application-generated IDs (UUID/TSID) and embed Value Objects.

### Key Principles

- Use **TSID** or **UUID** for primary keys
- Embed Value Objects with `@Embedded` and `@AttributeOverrides`
- Create a **protected no-arg constructor** for JPA
- Create a **public constructor** with all required fields
- Add **domain methods** that operate on entity state
- Use **optimistic locking** with `@Version`
- Extend `BaseEntity` for audit fields
- Validate state and throw exceptions for invalid operations

### Example: EventEntity

**File:** `events/domain/EventEntity.java`

```java
package dev.sivalabs.meetup4j.events.domain;

import dev.sivalabs.meetup4j.events.domain.vo.*;
import dev.sivalabs.meetup4j.shared.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "events")
class EventEntity extends BaseEntity {

    @EmbeddedId
    @AttributeOverride(name = "id", column = @Column(name = "id", nullable = false))
    private EventId id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "code", nullable = false, unique = true))
    private EventCode code;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "title", column = @Column(name = "title", nullable = false)),
        @AttributeOverride(name = "description", column = @Column(name = "description")),
        @AttributeOverride(name = "imageUrl", column = @Column(name = "image_url"))
    })
    private EventDetails details;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType type;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "capacity"))
    private Capacity capacity;
    
    //.. other fields

    @Version
    private int version;

    // Protected constructor for JPA
    protected EventEntity() {}

    // Constructor with all required fields
    public EventEntity(EventId id,
                       EventCode code,
                       EventDetails details,
                       Schedule schedule,
                       EventType type,
                       //...
                       EventLocation location) {
        this.id = id;
        this.code = code;
        this.details = details;
        this.schedule = schedule;
        this.type = type;
        //...
        this.location = location;
    }

    // Factory method for creating new entities
    public static EventEntity createDraft(
            EventDetails details,
            Schedule schedule,
            EventType type,
            TicketPrice ticketPrice,
            Capacity capacity,
            EventLocation location) {

        return new EventEntity(
                EventId.generate(),
                EventCode.generate(),
                details,
                schedule,
                type,
                EventStatus.DRAFT,
                ticketPrice,
                capacity,
                location);
    }

    // Domain logic methods
    public boolean hasFreeSeats() {
        return capacity == null || capacity.value() == null || capacity.value() > registrationsCount;
    }

    public boolean isPublished() {
        return status == EventStatus.PUBLISHED;
    }

    public boolean isCancelled() {
        return status == EventStatus.CANCELLED;
    }

    public boolean isStarted() {
        return schedule.startDatetime().isBefore(Instant.now());
    }

    public boolean publish() {
        if (this.isPublished()) {
            return false;
        }
        this.status = EventStatus.PUBLISHED;
        return true;
    }

    public boolean cancel() {
        if (this.isStarted()) {
            throw new EventCancellationException("Cannot cancel events that have already started");
        }
        if (this.isCancelled()) {
            return false;
        }
        this.status = EventStatus.CANCELLED;
        return true;
    }

    public EventEntity updateRegistrationsCount(int registrationsCount) {
        this.registrationsCount = registrationsCount;
        return this;
    }

    // Getters
}
```

To use TSID, add the following dependency:

```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-71</artifactId>
    <version>3.14.1</version>
</dependency>
```

Now you can use TSID to generate IDs as follows:

```java
import io.hypersistence.tsid.TSID;

public class TSIDUtil {
    private TSIDUtil() {}

    public static String generateTsidString() {
        return TSID.Factory.getTsid().toString();
    }

    public static Long generateTsidLong() {
        return TSID.Factory.getTsid().toLong();
    }
}
```

### BaseEntity with JPA Auditing

**File:** `shared/BaseEntity.java`

```java
package dev.sivalabs.meetup4j.shared;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    protected Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    protected Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

**Enable JPA Auditing** in your application configuration:

```java
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
```

---

## 4. Repositories

Repositories provide collection-like interfaces for aggregate roots with meaningful, business-focused method names.

### Key Principles

- Create repositories **only for aggregate roots**
- Use `@Query` with **JPQL** for custom queries
- Prefer **meaningful method names** over long Spring Data JPA finder methods
- Use **default methods** for convenience operations
- Use **constructor expressions** or **Projections** for read operations
- Return **domain objects** from write operations, **ViewModels** from read operations

### Example: EventRepository

**File:** `events/domain/EventRepository.java`

```java
package dev.sivalabs.meetup4j.events.domain;

import dev.sivalabs.meetup4j.events.domain.vo.EventCode;
import dev.sivalabs.meetup4j.events.domain.vo.EventId;
import dev.sivalabs.meetup4j.shared.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface EventRepository extends JpaRepository<EventEntity, EventId> {

    @Query("""
            SELECT e FROM EventEntity e
            WHERE e.status = dev.sivalabs.meetup4j.events.domain.EventStatus.PUBLISHED
            AND e.schedule.startDatetime > :now
            ORDER BY e.schedule.startDatetime ASC
            """)
    List<EventEntity> findUpcomingEvents(@Param("now") Instant now);

    @Query("""
            SELECT e FROM EventEntity e
            WHERE e.code = :code
            """)
    Optional<EventEntity> findByCode(@Param("code") EventCode code);

    // Convenience methods using default interface methods
    default EventEntity getEventById(EventId eventId) {
        return this.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
    }

    default EventEntity getByCode(EventCode eventCode) {
        return this.findByCode(eventCode)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with code: " + eventCode));
    }
}
```

### Benefits

- **Explicit queries**: Clear JPQL makes intent obvious
- **Type safety**: Uses Value Objects (`EventId`, `EventCode`)
- **Convenience methods**: `getEventById()` and `getByCode()` throw exceptions if not found
- **Business language**: Method names reflect domain concepts

---

## 5. Services

Separate **write operations** (commands) from **read operations** (queries) following **CQRS principles**.

### Key Principles

- **Write Services** (`*Service`):
  - Annotated with `@Transactional`
  - Accept **Commands** as input
  - Return **Value Objects** or void
  - Perform state changes
  - Publish domain events

- **Read Services** (`*QueryService`):
  - Annotated with `@Transactional(readOnly = true)`
  - Accept **Value Objects** as input
  - Return **ViewModels** (with primitives)
  - No state changes
  - Optimized for read performance

### Example 1: Write Service

**File:** `events/domain/EventService.java`

```java
package dev.sivalabs.meetup4j.events.domain;

import dev.sivalabs.meetup4j.events.domain.events.EventCancelled;
import dev.sivalabs.meetup4j.events.domain.events.EventCreated;
import dev.sivalabs.meetup4j.events.domain.events.EventPublished;
import dev.sivalabs.meetup4j.events.domain.vo.EventCode;
import dev.sivalabs.meetup4j.events.domain.vo.EventId;
import dev.sivalabs.meetup4j.shared.SpringEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventService {
    private final EventRepository eventRepository;
    private final SpringEventPublisher eventPublisher;

    EventService(EventRepository eventRepository, SpringEventPublisher eventPublisher) {
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
    }

    public EventCode createEvent(CreateEventCmd cmd) {
        var event = EventEntity.createDraft(
                cmd.details(),
                cmd.schedule(),
                cmd.type(),
                cmd.ticketPrice(),
                cmd.capacity(),
                cmd.location()
        );

        eventRepository.save(event);
        eventPublisher.publish(new EventCreated(
            event.getCode().code(),
            event.getDetails().title(),
            event.getDetails().description()
        ));
        return event.getCode();
    }

    public void cancelEvent(CancelEventCmd cmd) {
        EventEntity event = eventRepository.getByCode(cmd.eventCode());
        if(event.cancel()) {
            eventRepository.save(event);
            eventPublisher.publish(new EventCancelled(
                event.getCode().code(),
                event.getDetails().title(),
                event.getDetails().description()
            ));
        }
    }

    //...
    //...
}
```

### Example 2: Read Service (Query Service)

**File:** `events/domain/EventQueryService.java`

```java
package dev.sivalabs.meetup4j.events.domain;

import dev.sivalabs.meetup4j.events.domain.models.EventVM;
import dev.sivalabs.meetup4j.events.domain.vo.EventCode;
import dev.sivalabs.meetup4j.events.domain.vo.EventId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventQueryService {
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    EventQueryService(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    public List<EventVM> getUpcomingEvents() {
        return eventRepository.findUpcomingEvents(Instant.now())
                .stream().map(eventMapper::toEventVM).toList();
    }

    public EventVM getByCode(EventCode eventCode) {
        var event = eventRepository.getByCode(eventCode);
        return eventMapper.toEventVM(event);
    }

    //...
}
```

**ViewModel Example:**

```java
public record EventVM(
    Long id,
    String code,
    String title,
    String description,
    Instant startDatetime,
    Instant endDatetime,
    //...
    //...
    String venue,
    String virtualLink,
    int registeredUsersCount) {}
```

**Mapper Example:**

```java
@Component
class EventMapper {
    EventVM toEventVM(EventEntity event) {
        return new EventVM(
            event.getId().id(),
            event.getCode().code(),
            event.getDetails().title(),
            event.getDetails().description(),
            //...
            event.getLocation().virtualLink(),
            event.getRegistrationsCount()
        );
    }
}
```

---

## 6. Web REST API Layer

The REST layer handles HTTP concerns, converting between HTTP DTOs and domain objects.

### Key Principles

- Use **converters** to bind `@PathVariable` and `@RequestParam` to Value Objects
- Use **Jackson** for `@RequestBody` binding to Request Objects with Value Object properties
- Use `@JsonUnwrapped` to map flattened JSON to nested objects
- Validate with `@Valid` annotation
- Return appropriate HTTP status codes
- Delegate to services for business logic

### Example 1: REST Controller

**File:** `events/rest/EventsController.java`

```java
package dev.sivalabs.meetup4j.events.rest;

import dev.sivalabs.meetup4j.events.domain.*;
import dev.sivalabs.meetup4j.events.domain.models.EventVM;
import dev.sivalabs.meetup4j.events.domain.vo.EventCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/events")
class EventsController {
    private final EventService eventService;
    private final EventQueryService eventQueryService;

    EventsController(EventService eventService, EventQueryService eventQueryService) {
        this.eventService = eventService;
        this.eventQueryService = eventQueryService;
    }

    @GetMapping("")
    EventsResponse findEvents() {
        var events = eventQueryService.getUpcomingEvents();
        return new EventsResponse(events);
    }

    @GetMapping("/all")
    EventsResponse findAllEvents() {
        List<EventVM> events = eventQueryService.findAllEvents();
        return new EventsResponse(events);
    }

    @GetMapping("/{eventCode}")
    ResponseEntity<EventVM> findEventByCode(@PathVariable EventCode eventCode) {
        var event = eventQueryService.getByCode(eventCode);
        return ResponseEntity.ok(event);
    }

    @PostMapping
    ResponseEntity<CreateEventResponse> createEvent(
            @RequestBody @Valid CreateEventRequest request) {
        var cmd = new CreateEventCmd(
                request.details(),
                request.schedule(),
                request.type(),
                request.ticketPrice(),
                request.capacity(),
                request.location()
        );

        EventCode eventCode = eventService.createEvent(cmd);
        return ResponseEntity.status(CREATED).body(new CreateEventResponse(eventCode.code()));
    }

    @PatchMapping("/{eventCode}/publish")
    ResponseEntity<Void> publishEvent(@PathVariable EventCode eventCode) {
        var cmd = new PublishEventCmd(eventCode);
        eventService.publishEvent(cmd);
        return ResponseEntity.ok().build();
    }

}
```

### Example 2: Request DTO with @JsonUnwrapped

**File:** `events/rest/CreateEventRequest.java`

```java
package dev.sivalabs.meetup4j.events.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.sivalabs.meetup4j.events.domain.EventType;
import dev.sivalabs.meetup4j.events.domain.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
        @JsonUnwrapped
        @Valid
        EventDetails details,

        @JsonUnwrapped
        @Valid
        Schedule schedule,

        @NotNull(message = "Event type is required")
        EventType type,
        
        //...
        
        @Valid TicketPrice ticketPrice

        ) {
}
```

**Sample JSON Request:**

```json
{
  "title": "Spring Boot Workshop",
  "description": "Learn Spring Boot best practices",
  "imageUrl": "https://example.com/image.jpg",
  "startDatetime": "2024-12-01T10:00:00Z",
  "endDatetime": "2024-12-01T17:00:00Z",
  "type": "OFFLINE",
  "ticketPrice":  50.00,
  "capacity": 100,
  "venue": "Tech Hub Building A",
  "virtualLink": null
}
```

Notice how `@JsonUnwrapped` flattens the nested `EventDetails`, `Schedule`, and `EventLocation` properties into the top level of the JSON.

### Example 3: Converter for PathVariable Binding

**File:** `events/rest/converters/StringToEventCodeConverter.java`

```java
package dev.sivalabs.meetup4j.events.rest.converters;

import dev.sivalabs.meetup4j.events.domain.vo.EventCode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToEventCodeConverter implements Converter<String, EventCode> {

    @Override
    public EventCode convert(String source) {
        return new EventCode(source);
    }
}
```

This allows Spring MVC to automatically convert path variables like `/{eventCode}` from String to `EventCode`:

```java
@GetMapping("/{eventCode}")
ResponseEntity<EventVM> findEventByCode(@PathVariable EventCode eventCode) {
    // eventCode is already an EventCode object, not a String
}
```

---

## 7. Module Public API

Expose a **facade** (public API) from each module containing only the methods required by other modules.

### Key Principles

- Use a `*API` service class
- Mark it with `@Service`
- Delegate to internal services
- Only expose what other modules need
- Keep internal implementation details hidden

### Example: EventsAPI

**File:** `events/EventsAPI.java`

```java
package dev.sivalabs.meetup4j.events;

import dev.sivalabs.meetup4j.events.domain.EventQueryService;
import dev.sivalabs.meetup4j.events.domain.EventService;
import dev.sivalabs.meetup4j.events.domain.models.EventVM;
import dev.sivalabs.meetup4j.events.domain.vo.EventCode;
import dev.sivalabs.meetup4j.events.domain.vo.EventId;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventsAPI {
    private final EventService eventService;
    private final EventQueryService eventQueryService;

    public EventsAPI(EventService eventService, EventQueryService eventQueryService) {
        this.eventService = eventService;
        this.eventQueryService = eventQueryService;
    }

    public List<EventVM> getEventsByIds(List<EventId> eventIds) {
        return eventQueryService.getEventsByIds(eventIds);
    }

    public void reserveSlotForEvent(EventId eventId) {
        eventService.reserveSlotForEvent(eventId);
    }
    //...
}
```

**Usage in another module (registrations):**

```java
@Service
@Transactional
public class EventRegistrationService {
    private final EventsAPI eventsAPI;  // Injected from events module

    public RegistrationCode registerForEvent(RegisterAttendeeCmd cmd) {
        // Call public API instead of internal services
        eventsAPI.reserveSlotForEvent(eventId);
        // ... rest of registration logic
    }
}
```

---

## 8. Custom Exceptions

Create **domain-specific exceptions** that represent business rule violations.

### Key Principles

- Extend a base `DomainException`
- Use descriptive names that reflect business concepts
- Include meaningful error messages
- Throw from domain entities and services

### Example 1: Base Domain Exception

**File:** `shared/DomainException.java`

```java
package dev.sivalabs.meetup4j.shared;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
```

### Example 2: Resource Not Found Exception

**File:** `shared/ResourceNotFoundException.java`

```java
package dev.sivalabs.meetup4j.shared;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

### Example 3: Business-Specific Exceptions

**File:** `events/domain/InvalidEventCreationException.java`

```java
package dev.sivalabs.meetup4j.events.domain;

import dev.sivalabs.meetup4j.shared.DomainException;

public class InvalidEventCreationException extends DomainException {
    public InvalidEventCreationException(String message) {
        super(message);
    }
}
```

**File:** `events/domain/EventCancellationException.java`

```java
package dev.sivalabs.meetup4j.events.domain;

import dev.sivalabs.meetup4j.shared.DomainException;

public class EventCancellationException extends DomainException {
    public EventCancellationException(String message) {
        super(message);
    }
}
```

**File:** `events/domain/EventSlotReservationException.java`

```java
package dev.sivalabs.meetup4j.events.domain;

import dev.sivalabs.meetup4j.shared.DomainException;

public class EventSlotReservationException extends DomainException {
    public EventSlotReservationException(String message) {
        super(message);
    }
}
```

**Usage in Entity:**

```java
public boolean cancel() {
    if (this.isStarted()) {
        throw new EventCancellationException("Cannot cancel events that have already started");
    }
    // ... rest of logic
}
```

---

## 9. Global Exception Handler

Create a centralized exception handler that returns **ProblemDetail** responses.

### Key Principles

- Use `@RestControllerAdvice`
- Extend `ResponseEntityExceptionHandler`
- Return `ProblemDetail` for RFC 7807 compliance
- Map different exceptions to appropriate HTTP status codes
- Include validation errors in response
- Hide internal details in production

### Example: GlobalExceptionHandler

**File:** `config/GlobalExceptionHandler.java`

```java
package dev.sivalabs.meetup4j.config;

import dev.sivalabs.meetup4j.shared.DomainException;
import dev.sivalabs.meetup4j.shared.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Environment environment;

    GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Validation error", ex);
        var errors = ex.getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .toList();

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(UNPROCESSABLE_CONTENT, ex.getMessage());
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("errors", errors);
        return ResponseEntity.status(UNPROCESSABLE_CONTENT).body(problemDetail);
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handle(DomainException e) {
        log.info("Bad request", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(UNPROCESSABLE_CONTENT, e.getMessage());
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("errors", List.of(e.getMessage()));
        return problemDetail;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handle(ResourceNotFoundException e) {
        log.error("Resource not found", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(NOT_FOUND, e.getMessage());
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setProperty("errors", List.of(e.getMessage()));
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        logger.error("Unexpected exception occurred", e);

        // Don't expose internal details in production
        String message = "An unexpected error occurred";
        if (isDevelopmentMode()) {
            message = e.getMessage();
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, message);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    private boolean isDevelopmentMode() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        return profiles.contains("dev") || profiles.contains("local");
    }
}
```

### Error Response Examples

**Validation Error (422):**
```json
{
  "type": "about:blank",
  "title": "Validation Error",
  "status": 422,
  "detail": "Validation failed for argument...",
  "errors": [
    "Title is required",
    "Email must be valid"
  ]
}
```

**Domain Exception (422):**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 422,
  "detail": "Cannot cancel events that have already started",
  "errors": [
    "Cannot cancel events that have already started"
  ]
}
```

**Resource Not Found (404):**
```json
{
  "type": "about:blank",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Event not found with code: ABC123",
  "errors": [
    "Event not found with code: ABC123"
  ]
}
```

**Internal Server Error (500):**
```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "An unexpected error occurred",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Summary

This document demonstrates Spring Boot best practices using real examples from the `meetup4j-modulith-tomato` project:

1. **Package Structure**: Domain-driven modular organization
2. **Value Objects**: Immutable records with validation and Jackson support
3. **Entities**: Rich domain models with TSID IDs, embedded value objects, and business logic
4. **Repositories**: Type-safe interfaces with meaningful queries
5. **Services**: Separate write and read services (CQRS)
6. **REST API**: Controllers with converters and proper HTTP semantics
7. **Module API**: Public facades for inter-module communication
8. **Exceptions**: Domain-specific exception hierarchy
9. **Error Handling**: Centralized handler with ProblemDetail responses

These patterns promote clean architecture, type safety, and maintainability in Spring Boot applications.
