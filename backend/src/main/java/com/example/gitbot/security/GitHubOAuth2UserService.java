package com.example.gitbot.security;

import com.example.gitbot.entity.User;
import com.example.gitbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;
    private final DefaultOAuth2UserService defaultService = new  DefaultOAuth2UserService();

    @Override
    public @Nullable OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User gitUser = defaultService.loadUser(userRequest);

        String accessToken = userRequest.getAccessToken().getTokenValue();

        String scopes = userRequest.getAccessToken().getScopes() != null
                        ?
                        String.join(",", userRequest.getAccessToken().getScopes())
                        :
                        "read:user,repo";

        User user = userService.upsertFromGitHub(gitUser.getAttributes(), accessToken, scopes);

        return new AppUserPrincipal(user, gitUser.getAttributes());
    }
}
