package uz.zero_one.chat

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.Principal

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val userService: UserService) {

    @PostMapping("/login")
    fun telegramLogin(@RequestBody request: TelegramLoginRequestDto): JwtResponseDto {
        return userService.login(request)
    }

    @GetMapping("/token-me")
    fun tokenMe(): GetOneUserResponseDto{
        return userService.tokenMe()
    }

}

@Controller
@RequestMapping("/api/v1/auth")
class HtmlController {

    @GetMapping("/login-page")
    fun loginPage(): String {
        return "html"
    }

    @GetMapping("/")
    fun getAllUsers() : String{
        return "index"
    }
}

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserServiceImpl){

    @PutMapping("/{id}/update")
    fun updateUser(@PathVariable id: Long, @RequestPart("data") request: UserUpdateRequestDto, @RequestPart("file", required = false) file: MultipartFile?) {
        println("Update")
        userService.updateUser(id, request, file)
    }

    @DeleteMapping("/{id}/delete")
    fun deleteUser(@PathVariable id: Long){
        userService.deleteUser(id)
    }

    @GetMapping("/get-all-users-by-username-search")
    fun getAllUsersByUsernameSearch(@RequestParam(required = false) search: String?,pageable: Pageable): Page<GetOneUserResponseDto>{
        return userService.getAllUsersByUsernameSearch(search,pageable)
    }

    @GetMapping("/{id}/status")
    fun getUserStatus(@PathVariable id: Long): UserStatusDto {
         return userService.getUserStatus(id)
    }

}

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(private val chatService: ChatServiceImpl){

    @MessageMapping("/chat.sendMessage")
    fun sendMessage(requestDto: MessageRequestDto,principal: Principal) {
        val username = principal.name
        println("Request messagega keldi = $username")
        chatService.sendMessage(requestDto,username)
    }

    @PostMapping
    fun createPublicChat(@RequestBody requestDto: CreatePublicChatRequestDto){
        chatService.createPublicChat(requestDto)
    }

    @PostMapping("/add-members/{chatId}")
    fun addMembers(@PathVariable chatId: Long, @RequestBody requestDto: AddMembersRequestDto){
        chatService.addMembers(chatId,requestDto)
    }

    @GetMapping("/users")
    fun getChats(pageable: Pageable): Page<ChatListItemDto> {
        return chatService.getChatList(pageable)
    }

    @MessageMapping("/chat.read")
    fun markAsReadMessage(dto: ReadMessageRequestDto,
                   principal: Principal) {
        chatService.markMessagesAsRead(dto, principal.name)
    }

    @GetMapping("/{chatId}/messages")
    fun getMessages(@PathVariable chatId: Long,pageable: Pageable): Page<MessageResponseDto> {
       return chatService.getAllMessage(chatId,pageable)
    }

    @MessageMapping("/message.edit")
    fun editMessage(requestDto: EditMessageRequestDto, principal: Principal){
        val username = principal.name
        chatService.editMessage(requestDto.chatId,requestDto.messageId,requestDto.message,username)
    }

    @MessageMapping("/message.delete")
    fun deleteMessage(requestDto: DeleteMessageRequestDto,principal: Principal){
        val username = principal.name
        chatService.deleteMessage(requestDto,username)
    }

    @PostMapping("/upload")
    fun uploadFile(@RequestParam("file") file: MultipartFile): FileResponseDto {
        return chatService.saveFile(file)
    }
}

