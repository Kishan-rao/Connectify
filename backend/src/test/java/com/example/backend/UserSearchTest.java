package com.example.backend;

import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.UserSearchResultDto;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UserSearchTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User kishan, kishore, kishanth, alice;

    @BeforeEach
    void setUp() {
        kishan = saveUser("kishan");
        kishore = saveUser("kishore");
        kishanth = saveUser("kishanth");
        alice = saveUser("alice");
    }

    @Test
    void searchIsCaseInsensitive() {
        PagedResponse<UserSearchResultDto> resLower = userService.searchUsers(principal(alice), "kish", 0, 10);
        PagedResponse<UserSearchResultDto> resUpper = userService.searchUsers(principal(alice), "KISH", 0, 10);

        assertThat(resLower.getContent()).hasSize(3);
        assertThat(resUpper.getContent()).hasSize(3);
    }

    @Test
    void searchHandlesWhitespace() {
        PagedResponse<UserSearchResultDto> res = userService.searchUsers(principal(alice), "  alice  ", 0, 10);
        assertThat(res.getContent()).hasSize(1);
        assertThat(res.getContent().get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    void emptySearchReturnsEmptyPage() {
        PagedResponse<UserSearchResultDto> res = userService.searchUsers(principal(alice), "   ", 0, 10);
        assertThat(res.getContent()).isEmpty();
    }

    @Test
    void searchResultDoesNotExposeEmail() {
        PagedResponse<UserSearchResultDto> res = userService.searchUsers(principal(alice), "kishan", 0, 10);
        UserSearchResultDto item = res.getContent().get(0);
        assertThat(item.getUsername()).isEqualTo("kishan");
        assertThat(item).isInstanceOf(UserSearchResultDto.class);
    }

    @Test
    void searchPaginationWorks() {
        PagedResponse<UserSearchResultDto> p0 = userService.searchUsers(principal(alice), "kish", 0, 2);
        PagedResponse<UserSearchResultDto> p1 = userService.searchUsers(principal(alice), "kish", 1, 2);

        assertThat(p0.getContent()).hasSize(2);
        assertThat(p0.getTotalElements()).isEqualTo(3L);
        assertThat(p0.getTotalPages()).isEqualTo(2);
        assertThat(p1.getContent()).hasSize(1);
    }

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .role(Role.USER)
                .build());
    }

    private static Principal principal(User u) {
        return u::getUsername;
    }
}
