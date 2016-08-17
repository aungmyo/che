/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.user.server.jpa;

import com.google.inject.persist.Transactional;

import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.jdbc.jpa.DuplicateKeyException;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.user.server.spi.UserDao;
import org.eclipse.che.security.PasswordEncryptor;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author Yevhenii Voevodin
 */
@Singleton
public class JpaUserDao implements UserDao {

    @Inject
    protected Provider<EntityManager> managerProvider;
    @Inject
    private   PasswordEncryptor       encryptor;

    @Override
    @Transactional
    public UserImpl getByAliasAndPassword(String emailOrName, String password) throws NotFoundException, ServerException {
        requireNonNull(emailOrName, "Required non-null email or name");
        requireNonNull(password, "Required non-null password");
        try {
            final UserImpl user = managerProvider.get()
                                                 .createNamedQuery("User.getByAliasAndPassword", UserImpl.class)
                                                 .setParameter("alias", emailOrName)
                                                 .getSingleResult();
            if (!encryptor.test(password, user.getPassword())) {
                throw new NotFoundException(format("User with email or name '%s' and given password doesn't exist", emailOrName));
            }
            return user;
        } catch (NoResultException x) {
            throw new NotFoundException(format("User with email or name '%s' and given password doesn't exist", emailOrName));
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    public void create(UserImpl user) throws ConflictException, ServerException {
        requireNonNull(user, "Required non-null user");
        try {
            doCreate(user);
        } catch (DuplicateKeyException x) {
            // TODO make more concrete
            throw new ConflictException("User with such id/name/email/alias already exists");
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    public void update(UserImpl update) throws NotFoundException, ServerException, ConflictException {
        requireNonNull(update, "Required non-null update");
        try {
            doUpdate(update);
        } catch (DuplicateKeyException x) {
            // TODO make more concrete
            throw new ConflictException("User with such name/email/alias already exists");
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    public void remove(String id) throws ServerException, ConflictException {
        requireNonNull(id, "Required non-null id");
        try {
            doRemove(id);
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public UserImpl getByAlias(String alias) throws NotFoundException, ServerException {
        requireNonNull(alias, "Required non-null alias");
        try {
            return managerProvider.get()
                                  .createNamedQuery("User.getByAlias", UserImpl.class)
                                  .setParameter("alias", alias)
                                  .getSingleResult();
        } catch (NoResultException x) {
            throw new NotFoundException(format("User with alias '%s' doesn't exist", alias));
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public UserImpl getById(String id) throws NotFoundException, ServerException {
        requireNonNull(id, "Required non-null id");
        try {
            final UserImpl user = managerProvider.get().find(UserImpl.class, id);
            if (user == null) {
                throw new NotFoundException(format("User with id '%s' doesn't exist", id));
            }
            return user;
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public UserImpl getByName(String name) throws NotFoundException, ServerException {
        requireNonNull(name, "Required non-null name");
        try {
            return managerProvider.get()
                                  .createNamedQuery("User.getByName", UserImpl.class)
                                  .setParameter("name", name)
                                  .getSingleResult();
        } catch (NoResultException x) {
            throw new NotFoundException(format("User with name '%s' doesn't exist", name));
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public UserImpl getByEmail(String email) throws NotFoundException, ServerException {
        requireNonNull(email, "Required non-null email");
        try {
            return managerProvider.get()
                                  .createNamedQuery("User.getByEmail", UserImpl.class)
                                  .setParameter("email", email)
                                  .getSingleResult();
        } catch (NoResultException x) {
            throw new NotFoundException(format("User with email '%s' doesn't exist", email));
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public Page<UserImpl> getAll(int maxItems, int skipCount) throws ServerException {
        checkArgument(maxItems >= 0, "The number of items to return can't be negative.");
        checkArgument(skipCount >= 0, "The number of items to skip can't be negative.");
        try {
            List<UserImpl> list = managerProvider.get()
                                                 .createNamedQuery("User.getAll", UserImpl.class)
                                                 .setMaxResults(maxItems)
                                                 .setFirstResult(skipCount)
                                                 .getResultList();
            return new Page<>(list, skipCount, maxItems, getTotalCount());
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Transactional
    protected long getTotalCount() throws ServerException {
        try {
            return managerProvider.get().createNamedQuery("User.getTotalCount", Long.class).getSingleResult();
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Transactional
    protected void doCreate(UserImpl user) {
        managerProvider.get().persist(user);
    }

    @Transactional
    protected void doUpdate(UserImpl update) throws NotFoundException {
        final EntityManager manager = managerProvider.get();
        if (manager.find(UserImpl.class, update.getId()) == null) {
            throw new NotFoundException(format("Couldn't update user with id '%s' because it doesn't exist", update.getId()));
        }
        manager.merge(update);
    }

    @Transactional
    protected void doRemove(String id) {
        final EntityManager manager = managerProvider.get();
        final UserImpl user = manager.find(UserImpl.class, id);
        if (user != null) {
            manager.remove(user);
        }
    }
}
