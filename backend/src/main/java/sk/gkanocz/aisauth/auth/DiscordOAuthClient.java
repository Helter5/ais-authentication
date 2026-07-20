package sk.gkanocz.aisauth.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
class DiscordOAuthClient {

    private static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String USER_URL = "https://discord.com/api/users/@me";

    private final DiscordOAuthProperties discordOAuthProperties;
    private final RestClient restClient = RestClient.create();

    DiscordTokenResponse exchangeCodeForToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", discordOAuthProperties.clientId());
        form.add("client_secret", discordOAuthProperties.clientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", discordOAuthProperties.redirectUri());

        return restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(DiscordTokenResponse.class);
    }

    DiscordUserResponse fetchUser(String accessToken) {
        return restClient.get()
                .uri(USER_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(DiscordUserResponse.class);
    }

    record DiscordTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    record DiscordUserResponse(String id, String username, String avatar) {
    }
}
