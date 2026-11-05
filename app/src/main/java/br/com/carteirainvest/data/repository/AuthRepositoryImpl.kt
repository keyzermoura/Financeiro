package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.security.PasswordHasher
import br.com.carteirainvest.core.security.SessionManager
import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.data.local.dao.UserDao
import br.com.carteirainvest.data.local.entity.UserEntity
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.domain.exception.AuthException
import br.com.carteirainvest.domain.model.User
import br.com.carteirainvest.domain.model.UserSession
import br.com.carteirainvest.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val passwordHasher: PasswordHasher,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override val session: StateFlow<UserSession?> = sessionManager.session

    override suspend fun register(name: String, email: String, password: String): User =
        withContext(ioDispatcher) {
            val normalizedEmail = email.trim().lowercase()
            if (userDao.findByEmail(normalizedEmail) != null) {
                throw AuthException("Já existe uma conta com este e-mail.")
            }
            val salt = passwordHasher.generateSalt()
            val hash = passwordHasher.hash(password, salt)
            val entity = UserEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                email = normalizedEmail,
                passwordHash = hash,
                passwordSalt = salt,
                createdAt = System.currentTimeMillis(),
            )
            userDao.insert(entity)
            entity.toDomain()
        }

    override suspend fun authenticate(email: String, password: String): User =
        withContext(ioDispatcher) {
            val entity = userDao.findByEmail(email.trim().lowercase())
                ?: throw AuthException("E-mail ou senha inválidos.")
            if (!passwordHasher.verify(password, entity.passwordSalt, entity.passwordHash)) {
                throw AuthException("E-mail ou senha inválidos.")
            }
            entity.toDomain()
        }

    override fun startSession(user: User) {
        sessionManager.start(user)
    }

    override fun logout() {
        sessionManager.clear()
    }

    override fun setBiometricEnabled(enabled: Boolean) {
        sessionManager.setBiometricEnabled(enabled)
    }
}
