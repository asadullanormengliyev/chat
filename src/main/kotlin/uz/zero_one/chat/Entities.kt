package uz.zero_one.chat

import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.Date

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @CreatedDate @Temporal(TemporalType.TIMESTAMP) var createdDate: Date? = null,
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP) var modifiedDate: Date? = null,
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false
)

@Entity
@Table(name = "users")
class User(
    var firstName: String,
    val telegramId: Long,
    @Column(unique = true)
    var username: String,
    var avatarUrl: String?,
    var bio: String?,
    var authDate: Long,
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.OFFLINE,
    var lastSeen: LocalDateTime? = null
) : BaseEntity()

@Entity
class Chat(
    @Enumerated(EnumType.STRING)
    val chatType: ChatType,
    val groupName: String?,
    val avatarUrl: String?,
    @OneToOne
    var lastMessage: Message? = null,
    @OneToMany(mappedBy = "chat")
    val members: MutableList<ChatMember> = mutableListOf()
) : BaseEntity()

@Entity
@Table(name = "chat_members")
class ChatMember(
    @ManyToOne
    val chat: Chat,
    @ManyToOne
    val user: User,
    @Enumerated(EnumType.STRING)
    var role: MemberRole = MemberRole.MEMBER,
    var joinedAt: LocalDateTime = LocalDateTime.now(),
    var deletedAt: LocalDateTime? = null
) : BaseEntity()

@Entity
class Message(
    @ManyToOne
    val chat: Chat,
    @ManyToOne
    val sender: User,
    @Enumerated(EnumType.STRING)
    var messageType: MessageType,
    var content: String? = null,
    var fileUrl: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    @ManyToOne
    var replyTo: Message?,
    var edited: Boolean = false
) : BaseEntity()

@Entity
@Table(name = "message_status")
class MessageStatus(
    @ManyToOne
    val message: Message,
    @ManyToOne
    val user: User,
    var isRead: Boolean = false,
    var readAt: LocalDateTime? = null
) : BaseEntity()


@Entity
@Table(name = "files")
class FileEntity(
    @ManyToOne
    val user: User,
    var originalName: String,
    var fileUrl: String,
    var size: Long,
    var extension: String?,
    @Enumerated(EnumType.STRING)
    var messageType: MessageType
) : BaseEntity()

