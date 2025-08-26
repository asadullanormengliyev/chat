package uz.zero_one.chat

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
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

    @PostMapping("/private-chat")
    fun startChat(@RequestParam userId: Long): GetOneChatResponseDto{
        return chatService.createPrivateChat(userId)
    }

    @MessageMapping("/chat.sendMessage")
    fun sendMessage(requestDto: MessageRequestDto,principal: Principal) {
        val username = principal.name
        println("Request messagega keldi = $username")
        chatService.sendMessage(requestDto,username)
    }

    @PostMapping
    fun createPublicChat(@RequestParam groupName: String,@RequestParam("file", required = false) file: MultipartFile?){
        chatService.createPublicChat(groupName,file)
    }

    @PostMapping("/add-members/{chatId}")
    fun addMembers(@PathVariable chatId: Long, @RequestBody requestDto: AddMembersRequestDto){
        chatService.addMembers(chatId,requestDto)
    }

    @GetMapping("/users")
    fun getChats(): List<ChatListItemDto> {
        return chatService.getChatList()
    }

    @PostMapping("/file/uploads")
    fun saveFile(@RequestParam("file") file: MultipartFile): FileUploadResponseDto{
        return chatService.saveFile(file)
    }

    @MessageMapping("/chat.read")
    fun markAsReadMessage(dto: ReadMessageRequestDto,
                   principal: Principal) {
        chatService.markMessagesAsRead(dto, principal.name)
    }

    @GetMapping("/{chatId}/messages")
    fun getMessages(@PathVariable chatId: Long, pageable: Pageable): Page<MessageResponseDto> {
        return chatService.getAllMessage(chatId,pageable)
    }

    @MessageMapping("/message.edit")
    fun editMessage(@RequestPart chatId: Long, @RequestParam messageId: Long, @RequestParam message: String?, principal: Principal){
        val username = principal.name
        chatService.editMessage(chatId,messageId,message,username)
    }
}

