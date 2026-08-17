package sk.gkanocz.aisauth.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.directory.StudentDirectoryService;
import sk.gkanocz.aisauth.directory.StudentRecord;
import sk.gkanocz.aisauth.directory.VerificationProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    private static final VerificationProperties PROPERTIES =
            new VerificationProperties(List.of("fei-stud"), "student:active", false);

    @Mock
    private VerificationCodeRepository verificationCodeRepository;
    @Mock
    private VerifiedUserRepository verifiedUserRepository;
    @Mock
    private StudentDirectoryService studentDirectoryService;

    private VerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new VerificationService(
                verificationCodeRepository, verifiedUserRepository, studentDirectoryService, PROPERTIES);
    }

    private StudentRecord eligibleStudent() {
        return new StudentRecord("12345", "student@stuba.sk", List.of("fei-stud"),
                List.of("student:active"), "Jane", "Doe", "Jane Doe", "jdoe");
    }

    @Test
    void initiateVerificationRejectsNonNumericAisId() {
        assertThatThrownBy(() -> verificationService.initiateVerification("discord-1", "guild-1", "not-a-number"))
                .isInstanceOf(InvalidAisIdException.class);
    }

    @Test
    void initiateVerificationRejectsWhenDiscordUserAlreadyVerified() {
        when(verifiedUserRepository.existsByDiscordIdAndGuildId("discord-1", "guild-1")).thenReturn(true);

        assertThatThrownBy(() -> verificationService.initiateVerification("discord-1", "guild-1", "12345"))
                .isInstanceOf(AlreadyVerifiedException.class);
    }

    @Test
    void initiateVerificationRejectsWhenAisIdAlreadyVerifiedByAnotherUser() {
        when(verifiedUserRepository.existsByDiscordIdAndGuildId("discord-1", "guild-1")).thenReturn(false);
        when(verifiedUserRepository.existsByAisIdAndGuildId("12345", "guild-1")).thenReturn(true);

        assertThatThrownBy(() -> verificationService.initiateVerification("discord-1", "guild-1", "12345"))
                .isInstanceOf(AlreadyVerifiedException.class);
    }

    @Test
    void initiateVerificationRejectsUnknownAisId() {
        when(studentDirectoryService.findByAisId("12345")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.initiateVerification("discord-1", "guild-1", "12345"))
                .isInstanceOf(StudentNotFoundException.class);
    }

    @Test
    void initiateVerificationRejectsInactiveAccountStatus() {
        StudentRecord inactive = new StudentRecord("12345", "s@stuba.sk", List.of("fei-stud"),
                List.of("student:inactive"), "Jane", "Doe", "Jane Doe", "jdoe");
        when(studentDirectoryService.findByAisId("12345")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> verificationService.initiateVerification("discord-1", "guild-1", "12345"))
                .isInstanceOf(StudentNotEligibleException.class)
                .hasMessageContaining("active");
    }

    @Test
    void initiateVerificationRejectsWrongFaculty() {
        StudentRecord wrongFaculty = new StudentRecord("12345", "s@stuba.sk", List.of("fchpt-stud"),
                List.of("student:active"), "Jane", "Doe", "Jane Doe", "jdoe");
        when(studentDirectoryService.findByAisId("12345")).thenReturn(Optional.of(wrongFaculty));

        assertThatThrownBy(() -> verificationService.initiateVerification("discord-1", "guild-1", "12345"))
                .isInstanceOf(StudentNotEligibleException.class)
                .hasMessageContaining("faculty");
    }

    @Test
    void initiateVerificationSavesCodeAndClearsAnyPreviousPendingCode() {
        when(studentDirectoryService.findByAisId("12345")).thenReturn(Optional.of(eligibleStudent()));
        when(verificationCodeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationCode result = verificationService.initiateVerification("discord-1", "guild-1", "12345");

        verify(verificationCodeRepository).deleteByDiscordIdAndGuildId("discord-1", "guild-1");
        assertThat(result.getDiscordId()).isEqualTo("discord-1");
        assertThat(result.getGuildId()).isEqualTo("guild-1");
        assertThat(result.getEmail()).isEqualTo("student@stuba.sk");
        assertThat(result.getAisId()).isEqualTo("12345");
        assertThat(result.getCode()).hasSize(15);
        assertThat(result.isExpired()).isFalse();
    }

    @Test
    void confirmVerificationRejectsWhenNoPendingCode() {
        when(verificationCodeRepository.findByDiscordIdAndGuildIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("discord-1"), org.mockito.ArgumentMatchers.eq("guild-1"), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.confirmVerification("discord-1", "guild-1", "ABC123"))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void confirmVerificationRejectsWrongCode() {
        VerificationCode pending = new VerificationCode(
                "discord-1", "guild-1", "RIGHTCODE", "s@stuba.sk", "12345", LocalDateTime.now().plusMinutes(15));
        when(verificationCodeRepository.findByDiscordIdAndGuildIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("discord-1"), org.mockito.ArgumentMatchers.eq("guild-1"), any()))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> verificationService.confirmVerification("discord-1", "guild-1", "WRONGCODE"))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void confirmVerificationHandlesRaceConditionWhereAisIdGotVerifiedMeanwhile() {
        VerificationCode pending = new VerificationCode(
                "discord-1", "guild-1", "RIGHTCODE", "s@stuba.sk", "12345", LocalDateTime.now().plusMinutes(15));
        when(verificationCodeRepository.findByDiscordIdAndGuildIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("discord-1"), org.mockito.ArgumentMatchers.eq("guild-1"), any()))
                .thenReturn(Optional.of(pending));
        when(verifiedUserRepository.existsByAisIdAndGuildId("12345", "guild-1")).thenReturn(true);

        assertThatThrownBy(() -> verificationService.confirmVerification("discord-1", "guild-1", "RIGHTCODE"))
                .isInstanceOf(AlreadyVerifiedException.class);

        verify(verificationCodeRepository).deleteByDiscordIdAndGuildId("discord-1", "guild-1");
        verify(verifiedUserRepository, never()).save(any());
    }

    @Test
    void confirmVerificationSavesVerifiedUserAndDeletesPendingCode() {
        VerificationCode pending = new VerificationCode(
                "discord-1", "guild-1", "RIGHTCODE", "s@stuba.sk", "12345", LocalDateTime.now().plusMinutes(15));
        when(verificationCodeRepository.findByDiscordIdAndGuildIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("discord-1"), org.mockito.ArgumentMatchers.eq("guild-1"), any()))
                .thenReturn(Optional.of(pending));
        when(verifiedUserRepository.existsByAisIdAndGuildId("12345", "guild-1")).thenReturn(false);
        when(verifiedUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerifiedUser result = verificationService.confirmVerification("discord-1", "guild-1", "RIGHTCODE");

        assertThat(result.getAisId()).isEqualTo("12345");
        assertThat(result.getDiscordId()).isEqualTo("discord-1");
        verify(verificationCodeRepository).deleteByDiscordIdAndGuildId("discord-1", "guild-1");
    }

    @Test
    void manuallyVerifyRejectsIfDiscordUserAlreadyVerified() {
        when(verifiedUserRepository.existsByDiscordIdAndGuildId("discord-1", "guild-1")).thenReturn(true);

        assertThatThrownBy(() -> verificationService.manuallyVerify("discord-1", "guild-1", "12345", "s@stuba.sk"))
                .isInstanceOf(AlreadyVerifiedException.class);
    }

    @Test
    void manuallyVerifySavesWhenNotAlreadyVerified() {
        when(verifiedUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerifiedUser result = verificationService.manuallyVerify("discord-1", "guild-1", "12345", "s@stuba.sk");

        assertThat(result.getAisId()).isEqualTo("12345");
        assertThat(result.getEmail()).isEqualTo("s@stuba.sk");
    }

    @Test
    void cleanupDepartedMemberDeletesBothPendingCodesAndVerifiedRecord() {
        verificationService.cleanupDepartedMember("discord-1", "guild-1");

        verify(verificationCodeRepository).deleteByDiscordIdAndGuildId("discord-1", "guild-1");
        verify(verifiedUserRepository).deleteByDiscordIdAndGuildId("discord-1", "guild-1");
    }
}
