package com.assetshield.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetshield.auth.TestProps;
import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.domain.Role;
import com.assetshield.auth.domain.User;
import io.jsonwebtoken.Claims;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private static User user(UUID id) {
        User user = new User();
        setId(user, id);
        user.setPhoneNumber("+233200000001");
        user.setFullName("Ama Mensah");
        user.setRole(Role.OWNER);
        return user;
    }

    private static void setId(User user, UUID id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void issuedTokenParsesBackWithExpectedClaims() {
        TokenService service = new TokenService(TestProps.appProperties(3600));
        UUID userId = UUID.randomUUID();

        Claims claims = service.parse(service.issueAccessToken(user(userId)));

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("role")).isEqualTo("OWNER");
        assertThat(claims.get("phone")).isEqualTo("+233200000001");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void expiredTokenIsRejectedWithTokenExpired() {
        TokenService service = new TokenService(TestProps.appProperties(3600));
        String expired = service.issueAccessToken(user(UUID.randomUUID()), -60);

        assertThatThrownBy(() -> service.parse(expired))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }

    @Test
    void tamperedTokenIsRejectedWithTokenInvalid() {
        TokenService service = new TokenService(TestProps.appProperties(3600));
        String token = service.issueAccessToken(user(UUID.randomUUID()));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
    }

    @Test
    void garbageTokenIsRejectedWithTokenInvalid() {
        TokenService service = new TokenService(TestProps.appProperties(3600));

        assertThatThrownBy(() -> service.parse("not.a.jwt"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
    }
}
