package com.pda.boardapplication.controller;

import com.pda.apiutils.ApiUtils;
import com.pda.apiutils.GlobalExceptionResponse;
import com.pda.apiutils.GlobalResponse;
import com.pda.boardapplication.dto.BoardDto;
import com.pda.boardapplication.dto.UserDto;
import com.pda.boardapplication.service.BoardInteractionService;
import com.pda.boardapplication.service.BoardService;
import com.pda.exceptionhandler.exceptions.BadRequestException;
import com.pda.tofinsecurity.jwt.TokenableUser;
import com.pda.tofinsecurity.user.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "[Board]")
public class BoardController {

    private final BoardService boardService;
    private final BoardInteractionService boardInteractionService;

    @PostMapping
    @Operation(summary = "Register board item", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = GlobalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid Request body", content = @Content(schema = @Schema(implementation = GlobalExceptionResponse.class)))
    })
    public GlobalResponse<Object> registerBoard(@RequestBody @Valid BoardDto.RegisterReqDto registerReqDto, @AuthUser TokenableUser user) {
        log.debug("Register Board with title : {}", registerReqDto.getTitle());
        log.debug("Parsing jwt, got user id : {}", user.getId());
        Map<String, Object> result = new HashMap<>();

        try {
            UserDto.InfoDto userInfoDto = UserDto.InfoDto.fromTokenableUser(user);
            long boardId = boardService.registerBoard(registerReqDto, userInfoDto);
            log.debug("Board Item registered with PK : {}", boardId);
            result.put("boardId", boardId);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("Invalid category Id");
        }

        return ApiUtils.created("created", result);
    }

    @GetMapping("/{boardId}")
    @Operation(summary = "Get board item detail")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GlobalResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = GlobalExceptionResponse.class)))
    })
    public GlobalResponse<Object> getBoardDetail(@PathVariable("boardId") long boardId) {
        log.debug("Retrieve board with id : {}", boardId);
        Map<String, Object> result = new HashMap<>();

        BoardDto.DetailRespDto detailRespDto = boardService.getBoardDetail(boardId);
        result.put("board", detailRespDto);

        return ApiUtils.success("success", result);
    }

    @GetMapping
    @Operation(summary = "Get board list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "204", description = "No Content")
    })
    public GlobalResponse<Object> getBoardList(
            @RequestParam(required = false, defaultValue = "0", value = "pageNo") int pageNo,
            @RequestParam(required = false, defaultValue = "10", value = "size") int size,
            BoardDto.SearchConditionDto searchConditionDto
    ) {
        log.debug("Get board lists with page {}, size {}", pageNo, size);
        Map<String, Object> result = new HashMap<>();

        log.info(searchConditionDto.getCategory());

        List<BoardDto.AbstractRespDto> boards = boardService.getBoards(pageNo, size);
        result.put("boards", boards);

        return ApiUtils.success("success", result);
    }

    @PutMapping("/{boardId}")
    @Operation(summary = "Modify board", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public GlobalResponse<Object> modifyBoard(
            @RequestBody BoardDto.ModifyReqDto modifyReqDto,
            @PathVariable("boardId") long boardId,
            @AuthUser TokenableUser user
    ) {
        log.debug("Update board : {}", boardId);
        Map<String, Object> result = new HashMap<>();

        if((modifyReqDto.getTitle() == null || modifyReqDto.getTitle().isBlank())
            && (modifyReqDto.getContent() == null || modifyReqDto.getContent().isBlank())) {
            throw new BadRequestException("At least one property required");
        }

        UserDto.InfoDto userInfoDto = UserDto.InfoDto.fromTokenableUser(user);

        int count = boardService.modifyBoard(boardId, modifyReqDto, userInfoDto);
        result.put("modified", count);

        return ApiUtils.success("success", result);
    }

    @DeleteMapping("/{boardId}")
    @Operation(summary = "Delete board", security = @SecurityRequirement(name = "bearerAuth"))
    public GlobalResponse<Object> deleteBoard(@PathVariable("boardId") long boardId, @AuthUser TokenableUser user) {

        log.debug("Delete board : {}", boardId);
        Map<String, Object> result = new HashMap<>();

        UserDto.InfoDto userInfoDto = UserDto.InfoDto.fromTokenableUser(user);
        int count = boardService.deleteBoard(boardId, userInfoDto);
        result.put("deleted", count);

        return ApiUtils.success("success", result);
    }

    @PostMapping("/{boardId}/like")
    @Operation(summary = "Post like toggle event", security = @SecurityRequirement(name = "bearerAuth"))
    public GlobalResponse<Object> toggleLike(
            @PathVariable("boardId") long boardId,
            @AuthUser TokenableUser user
    ) {
        log.debug("Post like to board {}", boardId);
        Map<String, Object> result = new HashMap<>();

        UserDto.InfoDto userInfoDto = UserDto.InfoDto.fromTokenableUser(user);
        int count = boardInteractionService.toggleLike(boardId, userInfoDto);
        result.put("modifiedStatus", count > 0 ? "created" : "canceled");

        log.info("Like to board {} toggled : {}", boardId, count);

        return count > 0 ?
                ApiUtils.created("created",result) :
                ApiUtils.success("deleted", result);
    }

    @PostMapping("/{boardId}/bookmark")
    @Operation(summary = "Post bookmark toggle event", security = @SecurityRequirement(name = "bearerAuth"))
    public GlobalResponse<Object> toggleBookmark(
            @PathVariable("boardId") long boardId,
            @AuthUser TokenableUser user
    ) {
        log.debug("Post bookmark to board {}", boardId);
        Map<String, Object> result = new HashMap<>();

        UserDto.InfoDto userInfoDto = UserDto.InfoDto.fromTokenableUser(user);
        int count = boardInteractionService.toggleBookmark(boardId, userInfoDto);
        result.put("modifiedStatus", count > 0 ? "created" : "canceled");

        log.debug("Bookmark of board {} toggled {}", boardId, count);

        return count > 0 ?
                ApiUtils.created("created", result) :
                ApiUtils.success("deleted", result);
    }
}
