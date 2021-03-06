package com.shiftshop.service.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiftshop.service.model.common.exceptions.DuplicateInstancePropertyException;
import com.shiftshop.service.model.common.exceptions.InstanceNotFoundException;
import com.shiftshop.service.model.entities.User;
import com.shiftshop.service.model.entities.User.RoleType;
import com.shiftshop.service.model.entities.UserDao;
import com.shiftshop.service.model.services.*;
import com.shiftshop.service.rest.common.JwtGenerator;
import com.shiftshop.service.rest.common.JwtInfo;
import com.shiftshop.service.rest.dtos.user.AuthenticatedUserDto;
import com.shiftshop.service.rest.dtos.user.ChangePasswordParamsDto;
import com.shiftshop.service.rest.dtos.user.InsertUserParamsDto;
import com.shiftshop.service.rest.dtos.user.LoginParamsDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class UserControllerTest {

    private final Long NON_EXISTENT_ID = -1L;
    private final String USERNAME = "user";
    private final static String PASSWORD = "password";
    private final String NAME = "User";
    private final String SURNAMES = "Test Tester";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtGenerator jwtGenerator;

    @Autowired
    private UserDao userDao;

    @Autowired
    private UserService userService;

    @Autowired
    private UserController userController;

    private AuthenticatedUserDto createAuthenticatedUser(String userName, Set<RoleType> roles)
            throws IncorrectLoginException, UserNotActiveException,
            DuplicateInstancePropertyException, NoUserRolesException {

        User user = new User(userName, PASSWORD, NAME, SURNAMES, roles);

        userService.registerUser(user);

        LoginParamsDto loginParams = new LoginParamsDto();
        loginParams.setUserName(user.getUserName());
        loginParams.setPassword(PASSWORD);

        return userController.login(loginParams);
    }

    private AuthenticatedUserDto createAuthenticatedAdminUser(String userName)
            throws IncorrectLoginException, UserNotActiveException,
            DuplicateInstancePropertyException, NoUserRolesException {

        Set<RoleType> roles = new HashSet<>();
        roles.add(RoleType.ADMIN);

        return createAuthenticatedUser(userName, roles);
    }

    private AuthenticatedUserDto createAuthenticatedManagerUser(String userName)
            throws IncorrectLoginException, UserNotActiveException {

        // Register manager for test using DAO because service logic doesnt allow it
        User user = new User(userName, PASSWORD, NAME, SURNAMES, new HashSet<>(Arrays.asList(RoleType.MANAGER)));
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActive(true);

        userDao.save(user);

        LoginParamsDto loginParams = new LoginParamsDto();
        loginParams.setUserName(user.getUserName());
        loginParams.setPassword(PASSWORD);

        return userController.login(loginParams);
    }

    @Test
    public void testPostRegisterUser_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");


        // Insert user with password
        InsertUserParamsDto userParams = new InsertUserParamsDto();
        userParams.setUserName(USERNAME + "1");
        userParams.setPassword(PASSWORD);
        userParams.setName(NAME + "1");
        userParams.setSurnames(SURNAMES);
        userParams.setRoles(new HashSet<>(Arrays.asList(RoleType.ADMIN)));

        ObjectMapper mapper = new ObjectMapper();

        mockMvc.perform(post("/users" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(userParams)))
                .andExpect(status().isNoContent());

    }

    @Test
    public void testPostRegisterUser_BadRequest() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");
        ObjectMapper mapper = new ObjectMapper();

        // Try insert user without roles / insert user which unique role is MANAGER
        InsertUserParamsDto userParams = new InsertUserParamsDto();
        userParams.setUserName(USERNAME + "1");
        userParams.setPassword(PASSWORD);
        userParams.setName(NAME + "1");
        userParams.setSurnames(SURNAMES);
        userParams.setRoles(new HashSet<>(Arrays.asList(RoleType.MANAGER)));

        mockMvc.perform(post("/users" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(userParams)))
                .andExpect(status().isBadRequest());

        // Try insert user without required properties
        userParams = new InsertUserParamsDto();
        userParams.setRoles(new HashSet<>(Arrays.asList(RoleType.SALESMAN)));

        mockMvc.perform(post("/users" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(userParams)))
                .andExpect(status().isBadRequest());

    }

    @Test
    public void testPostRegisterUser_Conflict() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");
        ObjectMapper mapper = new ObjectMapper();

        // Try insert user with userName already registered
        InsertUserParamsDto userParams = new InsertUserParamsDto();
        userParams.setUserName(user.getUserLoggedDto().getUserName());
        userParams.setPassword(PASSWORD);
        userParams.setName(NAME);
        userParams.setSurnames(SURNAMES);
        userParams.setRoles(new HashSet<>(Arrays.asList(RoleType.ADMIN)));

        mockMvc.perform(post("/users" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(userParams)))
                .andExpect(status().isConflict());

    }

    @Test
    public void testPostRegisterUser_Forbidden() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");

        mockMvc.perform(post("/users" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

    }

    @Test
    public void testPostLogin_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");
        ObjectMapper mapper = new ObjectMapper();

        LoginParamsDto loginParams = new LoginParamsDto();
        loginParams.setUserName(user.getUserLoggedDto().getUserName());
        loginParams.setPassword(PASSWORD);

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(loginParams)))
                .andExpect(status().isOk());
    }

    @Test
    public void testPostLogin_NotFound() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");
        ObjectMapper mapper = new ObjectMapper();

        // Incorrect username
        LoginParamsDto loginParams = new LoginParamsDto();
        loginParams.setUserName("_" + user.getUserLoggedDto().getUserName());
        loginParams.setPassword(PASSWORD);

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(loginParams)))
                .andExpect(status().isNotFound());

        // Incorrect password
        loginParams = new LoginParamsDto();
        loginParams.setUserName(user.getUserLoggedDto().getUserName());
        loginParams.setPassword("_" + PASSWORD);

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(loginParams)))
                .andExpect(status().isNotFound());

        // Not active user
        User userInserted = userDao.findByUserName(user.getUserLoggedDto().getUserName()).get();
        userInserted.setActive(false);
        userDao.save(userInserted);

        loginParams = new LoginParamsDto();
        loginParams.setUserName(user.getUserLoggedDto().getUserName());
        loginParams.setPassword(PASSWORD);

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(loginParams)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testPostLogin_BadRequest() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");

        // Without loginParams
        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testPostLoginFromServiceToken_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");

        mockMvc.perform(post("/users/loginFromServiceToken" )
                .header("Authorization", "Bearer " + user.getServiceToken()))
                .andExpect(status().isOk());
    }

    @Test
    public void testPostLoginFromServiceToken_NotFound() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");

        String tokenWithNoExistentId = jwtGenerator.generate(
                new JwtInfo(NON_EXISTENT_ID, user.getUserLoggedDto().getUserName(), user.getUserLoggedDto().getRoles()));

        mockMvc.perform(post("/users/loginFromServiceToken" )
                .header("Authorization", "Bearer " + tokenWithNoExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetUserRoles_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");

        mockMvc.perform(get("/users/roles" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

    }

    @Test
    public void testGetUserRoles_Forbidden() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");

        mockMvc.perform(get("/users/roles" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

    }

    @Test
    public void testGetUsers_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");

        // Add blocked user
        User blockedUser = new User("user", PASSWORD, NAME, SURNAMES, new HashSet<>());

        blockedUser.setPassword(passwordEncoder.encode(blockedUser.getPassword()));
        blockedUser.setActive(false);

        userDao.save(blockedUser);

        mockMvc.perform(get("/users" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/users?onlyActive=false" )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

    }

    @Test
    public void testPutUpdateUser_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");
        ObjectMapper mapper = new ObjectMapper();

        User testUser = userService.registerUser(new User("newUser", PASSWORD, NAME, SURNAMES,
                new HashSet<>(Arrays.asList(RoleType.ADMIN))));

        // Update user params
        InsertUserParamsDto userParams = new InsertUserParamsDto();
        userParams.setName(NAME + "X");
        userParams.setSurnames(SURNAMES  + "X");
        userParams.setRoles(new HashSet<>(Arrays.asList(RoleType.SALESMAN)));

        mockMvc.perform(put("/users/" + testUser.getId() )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(userParams)))
                .andExpect(status().isOk());

    }

    @Test
    public void testPutUpdateUser_NotFound() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");
        ObjectMapper mapper = new ObjectMapper();

        // Update user params
        InsertUserParamsDto userParams = new InsertUserParamsDto();

        mockMvc.perform(put("/users/" + NON_EXISTENT_ID )
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(userParams)))
                .andExpect(status().isNotFound());

    }

    @Test
    public void testPutInactiveUser_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");

        User testUser = userService.registerUser(new User("newUser", PASSWORD, NAME, SURNAMES,
                new HashSet<>(Arrays.asList(RoleType.ADMIN))));

        // Block user
        mockMvc.perform(put("/users/" + testUser.getId() + "/inactive")
                .header("Authorization", "Bearer " + user.getServiceToken()))
                .andExpect(status().isNoContent());

    }

    @Test
    public void testPutInactiveUser_BadRequest() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");

        // Block user
        mockMvc.perform(put("/users/" + user.getUserLoggedDto().getId() + "/inactive")
                .header("Authorization", "Bearer " + user.getServiceToken()))
                .andExpect(status().isBadRequest());

    }

    @Test
    public void testPutInactiveUser_NotFound() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");

        // Block user
        mockMvc.perform(put("/users/" + NON_EXISTENT_ID + "/inactive")
                .header("Authorization", "Bearer " + user.getServiceToken()))
                .andExpect(status().isNotFound());

    }

    @Test
    public void testPutActiveUser_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");

        User testUser = userService.registerUser(new User("newUser", PASSWORD, NAME, SURNAMES,
                new HashSet<>(Arrays.asList(RoleType.ADMIN))));

        // Unblock user
        mockMvc.perform(put("/users/" + testUser.getId() + "/active")
                .header("Authorization", "Bearer " + user.getServiceToken()))
                .andExpect(status().isNoContent());

    }

    @Test
    public void testPutActiveUser_NotFound() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedManagerUser("manager");

        // Block user
        mockMvc.perform(put("/users/" + NON_EXISTENT_ID + "/active")
                .header("Authorization", "Bearer " + user.getServiceToken()))
                .andExpect(status().isNotFound());

    }

    @Test
    public void testPostUserChangePassword_Ok() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");

        ChangePasswordParamsDto params = new ChangePasswordParamsDto();
        params.setOldPassword(PASSWORD);
        params.setNewPassword("NEW" +  PASSWORD);

        ObjectMapper mapper = new ObjectMapper();

        mockMvc.perform(post("/users/" + user.getUserLoggedDto().getId() + "/changePassword")
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(params)))
                .andExpect(status().isNoContent());

    }

    @Test
    public void testPostUserChangePassword_NotFound() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");

        ChangePasswordParamsDto params = new ChangePasswordParamsDto();
        params.setOldPassword("X" + PASSWORD);
        params.setNewPassword(PASSWORD);

        ObjectMapper mapper = new ObjectMapper();

        mockMvc.perform(post("/users/" + user.getUserLoggedDto().getId() + "/changePassword")
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(params)))
                .andExpect(status().isNotFound());

        String tokenNonExistentId = jwtGenerator.generate(
                new JwtInfo(NON_EXISTENT_ID, user.getUserLoggedDto().getUserName(), user.getUserLoggedDto().getRoles()));

        params = new ChangePasswordParamsDto();
        params.setOldPassword(PASSWORD);
        params.setNewPassword("NEW" +  PASSWORD);

        mockMvc.perform(post("/users/" + NON_EXISTENT_ID + "/changePassword")
                .header("Authorization", "Bearer " + tokenNonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(params)))
                .andExpect(status().isNotFound());

    }

    @Test
    public void testPostUserChangePassword_Forbidden() throws Exception {

        AuthenticatedUserDto user = createAuthenticatedAdminUser("admin");

        ChangePasswordParamsDto params = new ChangePasswordParamsDto();
        params.setOldPassword(PASSWORD);
        params.setNewPassword("NEW" +  PASSWORD);

        ObjectMapper mapper = new ObjectMapper();

        mockMvc.perform(post("/users/" + NON_EXISTENT_ID + "/changePassword")
                .header("Authorization", "Bearer " + user.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(params)))
                .andExpect(status().isForbidden());

    }

}
