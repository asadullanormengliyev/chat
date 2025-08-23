package uz.zero_one.chat

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T>
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeleted(pageable: Pageable): Page<T>
}

class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>, entityManager: EntityManager
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb ->
        cb.equal(root.get<Boolean>("deleted"), false)
    }

    override fun findByIdAndDeletedFalse(id: Long): T? = findByIdOrNull(id)?.run { if (deleted) null else this }

    @Transactional
    override fun trash(id: Long): T? = findByIdOrNull(id)?.run {
        deleted = true
        save(this)
    }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)

    override fun findAllNotDeleted(pageable: Pageable): Page<T> = findAll(isNotDeletedSpecification, pageable)

    @Transactional
    override fun trashList(ids: List<Long>): List<T> = ids.map { trash(it)!! }
}

@Repository
interface UserRepository : BaseRepository<User> {
    fun findByTelegramIdAndDeletedFalse(telegramId: Long): User?

    @Query(
        """select u from User u where u.deleted = false and 
        (:search is null or :search = '' or u.username ilike concat('%',:search,'%') )"""
    )
    fun findAllUsersByUsernameSearch(
        @Param("search") search: String?,
        pageable: Pageable
    ): Page<User>
}

@Repository
interface ChatRepository : BaseRepository<Chat> {

    @Query(
        """
                select c from Chat c
                inner join ChatMember m1 on m1.chat = c and m1.user.id = :userId
                inner join ChatMember m2 on m2.chat = c and m2.user.id = :currentUserId
                where c.chatType = 'PRIVATE' and c.deleted = false 
                """
    )
    fun findPrivateChat(userId: Long, currentUserId: Long): Chat?


}

@Repository
interface ChatMemberRepository : BaseRepository<ChatMember> {
   fun findByChatIdAndDeletedFalse(id: Long): List<ChatMember>
    fun findByChatIdAndUserId(chatId: Long,userId: Long): ChatMember
    fun existsByChatIdAndUserId(chatId: Long,userId: Long): Boolean
    @Query("""
        select cm.chat from ChatMember cm 
        where cm.user.id = :userId
    """)
    fun findChatsByUserId(@Param("userId") userId: Long): List<Chat>

    @Query("""
        select cm from ChatMember cm 
        where cm.chat.id = :chatId
    """)
    fun findMembersByChatId(@Param("chatId") chatId: Long): List<ChatMember>
}

@Repository
interface MessageRepository : BaseRepository<Message> {

}

@Repository
interface MessageStatusRepository : BaseRepository<MessageStatus> {

}
