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


@RestController
@RequestMapping("/api/v1/auth")
/*@CrossOrigin(origins = ["*"])*/
class AuthController(private val userService: UserService) {

    @PostMapping("/login")
    fun telegramLogin(@RequestBody request: TelegramLoginRequestDto): JwtResponseDto {
        return userService.login(request)
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

    @DeleteMapping("/delete/{id}")
    fun deleteUser(@PathVariable id: Long){
        userService.deleteUser(id)
    }

    @GetMapping("/get-all-users-by-username-search")
    fun getAllUsersByUsernameSearch(@RequestParam(required = false) search: String?,pageable: Pageable): Page<GetOneUserResponseDto>{
        return userService.getAllUsersByUsernameSearch(search,pageable)
    }

}

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(private val chatService: ChatServiceImpl){

    @PostMapping("/private-chat")
    fun startChat(@RequestParam userId: Long): GetOneChatResponseDto{
        return chatService.createPrivateChat(userId)
    }

   /* @MessageMapping("/chat.sendMessage")
    fun sendMessage(@RequestPart("data") request: MessageRequestDto,
                      @RequestPart("file", required = false) file: MultipartFile?) {
        chatService.sendMessage(request,file)
    }*/

    @MessageMapping("/chat.sendMessage")
    fun sendMessage(@RequestBody requestDto: MessageRequestDto) {
        chatService.sendMessage(requestDto,null)
    }

    @PostMapping
    fun createPublicChat(@RequestParam groupName: String,@RequestParam("file", required = false) file: MultipartFile?){
        chatService.createPublicChat(groupName,file)
    }

    @PostMapping("/add-members")
    fun addMembers(@RequestParam chatId: Long,@RequestBody requestDto: AddMembersRequestDto){
        chatService.addMembers(chatId,requestDto)
    }

}

