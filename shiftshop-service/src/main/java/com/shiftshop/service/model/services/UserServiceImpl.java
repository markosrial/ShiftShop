package com.shiftshop.service.model.services;

import com.shiftshop.service.model.common.exceptions.DuplicateInstancePropertyException;
import com.shiftshop.service.model.common.exceptions.InstanceNotFoundException;
import com.shiftshop.service.model.common.exceptions.InstancePropertyNotFoundException;
import com.shiftshop.service.model.common.utils.MessageConstants;
import com.shiftshop.service.model.entities.User;
import com.shiftshop.service.model.entities.User.RoleType;
import com.shiftshop.service.model.entities.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    @Autowired
    private PermissionChecker permissionChecker;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private UserDao userDao;

    @Override
    public User registerUser(User user) throws DuplicateInstancePropertyException, NoUserRolesException {

        try {

            // Check if user with login exists
            permissionChecker.checkUser(user.getUserName());
            throw new DuplicateInstancePropertyException(MessageConstants.ENTITIES_USER,
                    MessageConstants.ENTITIES_PROPS_USERNAME, user.getUserName());

        } catch (InstancePropertyNotFoundException e) {

            // New manager users can not be registered
            user.getRoles().remove(RoleType.MANAGER);

            if (user.getRoles().isEmpty()) {
                throw new NoUserRolesException();
            }

            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setActive(true);

            return userDao.save(user);

        }
    }

    @Override
    @Transactional(readOnly = true)
    public User login(String userName, String password) throws IncorrectLoginException, UserNotActiveException {

        User user;

        try {
            user = permissionChecker.checkUser(userName);
        } catch (InstancePropertyNotFoundException e) {
            throw new IncorrectLoginException();
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IncorrectLoginException();
        }

        // We check if user is active after matching password to secure if some user is trying to
        // discover blocked accounts
        if (!user.isActive()) {
            throw new UserNotActiveException();
        }

        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public User loginFromId(Long id) throws InstanceNotFoundException, UserNotActiveException {

        User user = permissionChecker.checkUser(id);

        if (!user.isActive()) {
            throw new UserNotActiveException();
        }

        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public Block<User> getUsers(boolean onlyActive, int page, int size) {

        Slice<User> slice = onlyActive
                ? userDao.findByActiveIsTrueOrderByUserNameAsc(PageRequest.of(page, size))
                : userDao.findByOrderByUserNameAsc(PageRequest.of(page, size));

        return new Block<>(slice.getContent(), slice.hasNext());
    }

    @Override
    public LocalDateTime getLastUserUpdatedTimestamp() {

        Optional<LocalDateTime> lastUpdate = userDao.getLastUpdateTimestamp();

        if (lastUpdate.isEmpty()) {
            return LocalDateTime.MIN;
        }

        return lastUpdate.get();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getUpdatedUsers(LocalDateTime lastUpdate) {

        // With no user changes (have lastUpdate and last update on user is before or equal to lastUpdate passed)
        if (lastUpdate != null && !getLastUserUpdatedTimestamp().isAfter(lastUpdate)) {

            return new ArrayList<>();

        }

        return userDao.findAllByActiveIsTrueAndRolesContains(RoleType.SALESMAN);

    }

    @Override
    public User updateUser(Long id, String name, String surnames, Set<RoleType> roles)
            throws InstanceNotFoundException, NoUserRolesException {

        User user = permissionChecker.checkUser(id);

        if (roles != null) {

            if (user.getRoles().contains(RoleType.MANAGER)) {
                // Force to keep Manager role on managers
                roles.add(RoleType.MANAGER);
            } else {
                // Remove Manager role from no manager users
                roles.remove(RoleType.MANAGER);
            }

            if (roles.isEmpty()) {
                throw new NoUserRolesException();
            }

            user.setRoles(roles);
        }

        if (name != null) {
            user.setName(name);
        }

        if (surnames != null) {
            user.setSurnames(surnames);
        }

        return user;

    }

    @Override
    public void setActiveUser(Long id, boolean active) throws BlockUserException, InstanceNotFoundException {

        User user = permissionChecker.checkUser(id);

        if (user.getRoles().contains(RoleType.MANAGER)) {
            throw new BlockUserException();
        }

        user.setActive(active);

    }

    @Override
    public void changePassword(Long id, String oldPassword, String newPassword)
            throws InstanceNotFoundException, IncorrectPasswordException {

        User user = permissionChecker.checkUser(id);

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IncorrectPasswordException();
        } else {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

    }
}
