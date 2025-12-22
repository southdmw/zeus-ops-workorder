package com.gdu.zeus.ops.workorder.services;


import com.gdu.zeus.ops.workorder.dto.*;
import com.gdu.zeus.ops.workorder.entity.Chat;
import com.gdu.zeus.ops.workorder.entity.ChatDetail;
import com.gdu.zeus.ops.workorder.mapper.ChatDetailMapper;
import com.gdu.zeus.ops.workorder.mapper.ChatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    
    private final ChatMapper chatMapper;
    private final ChatDetailMapper chatDetailMapper;
    private final ChatMemory chatMemory;

    // 对话类型名称映射
    private static final Map<Integer, String> CHAT_TYPE_NAMES = Map.of(
        1, "目标检测",
        2, "告警研判",
        3, "智能问数",
        4, "创建工单"
    );

    /**
     * 获取对话列表（按时间段分组）
     */
    public ChatListGroupResponse getChatListGrouped(String userId) {
        // 计算时间范围
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay(); // 今天0点
        LocalDateTime yesterdayStart = todayStart.minusDays(1);      // 昨天0点
        LocalDateTime last7DaysStart = todayStart.minusDays(7);      // 7天前0点
        LocalDateTime last30DaysStart = todayStart.minusDays(30);    // 30天前0点

        // 查询30天内的所有对话
        List<Chat> chats = chatMapper.selectChatsByTimeRange(userId, last30DaysStart, now);

        // 创建分组响应对象
        ChatListGroupResponse groupResponse = new ChatListGroupResponse();

        // 遍历对话，按时间分组
        for (Chat chat : chats) {
            ChatListResponse response = new ChatListResponse();
            response.setChatId(chat.getChatId());
            response.setTitle(chat.getTitle());
            response.setCreateTime(chat.getCreateTime());

            LocalDateTime createTime = chat.getCreateTime();

            if (!createTime.isBefore(todayStart)) {
                // 今日
                groupResponse.getToday().add(response);
            } else if (!createTime.isBefore(yesterdayStart)) {
                // 昨日
                groupResponse.getYesterday().add(response);
            } else if (!createTime.isBefore(last7DaysStart)) {
                // 7日内（2-7天前）
                groupResponse.getLast7Days().add(response);
            } else {
                // 30日内（8-30天前）
                groupResponse.getLast30Days().add(response);
            }
        }

        return groupResponse;
    }

    /**
     * 获取对话详情(平铺结构)
     */
    public List<ChatDetail> getChatDetailAll(String chatId) {
        List<ChatDetail> details = chatDetailMapper.selectByChatId(chatId);
        return details;
    }

    /**
     * 获取对话详情
     */
    public List<ChatDetailResponse> getChatDetail(String chatId) {
        List<ChatDetailResponse> responses = new ArrayList<>();
        
        // 遍历四种对话类型
        for (int chatType = 1; chatType <= 4; chatType++) {
            ChatDetailResponse response = new ChatDetailResponse();
            response.setChatType(chatType);
            response.setChatTypeName(CHAT_TYPE_NAMES.get(chatType));
            
            // 查询该类型下的所有消息
            List<ChatDetail> details = chatDetailMapper.selectByChatIdAndType(chatId, chatType);
            
            // 按conversationId分组
            Map<String, List<ChatDetail>> groupedByConversation = details.stream()
                    .collect(Collectors.groupingBy(
                            detail -> detail.getConversationId() != null ? detail.getConversationId() : "null",
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            
            // 构建对话组
            List<ConversationGroup> conversations = new ArrayList<>();
            for (Map.Entry<String, List<ChatDetail>> entry : groupedByConversation.entrySet()) {
                ConversationGroup group = new ConversationGroup();
                group.setConversationId(entry.getKey().equals("null") ? null : entry.getKey());
                
                List<ChatDetail> detailList = entry.getValue();
                if (!detailList.isEmpty()) {
                    ChatDetail firstDetail = detailList.get(0);
                    group.setStopFlag(firstDetail.getConversationStopFlag());
                    group.setStopTime(firstDetail.getConversationStopTime());
                }
                
                // 构建消息列表，按时间倒序排列
                List<MessageItem> messages = detailList.stream()
                        .sorted(Comparator.comparing(ChatDetail::getCreateTime).reversed())
                        .map(detail -> {
                            MessageItem item = new MessageItem();
                            item.setId(detail.getId());
                            item.setContent(detail.getContent());
                            item.setRole(detail.getRole());
                            item.setCreateTime(detail.getCreateTime());
                            return item;
                        })
                        .collect(Collectors.toList());
                
                group.setMessages(messages);
                conversations.add(group);
            }
            
            // 按时间倒序排列对话组
            conversations.sort((a, b) -> {
                LocalDateTime timeA = a.getMessages().isEmpty() ? LocalDateTime.MIN : a.getMessages().get(0).getCreateTime();
                LocalDateTime timeB = b.getMessages().isEmpty() ? LocalDateTime.MIN : b.getMessages().get(0).getCreateTime();
                return timeB.compareTo(timeA);
            });
            
            response.setConversations(conversations);
            responses.add(response);
        }
        
        return responses;
    }
    
    /**
     * 创建新对话
     */
    @Transactional
    public String createChat(String userId, String firstQuery) {
        Chat chat = new Chat();
        chat.setChatId(UUID.randomUUID().toString());
        chat.setTitle(firstQuery); // 暂时使用第一次提问作为标题，后续可以调用大模型生成
        chat.setCreateBy(userId);
        chat.setCreateTime(LocalDateTime.now());
        chatMapper.insert(chat);
        return chat.getChatId();
    }
    
//    /**
//     * 发送消息（阻塞模式）
//     */
//    @Transactional
//    public ChatMessageResponse sendMessage(ChatMessageRequest request) {
//        // 1. 检查是否需要创建新对话
//        if (request.getChatId() == null || request.getChatId().isEmpty()) {
//            request.setChatId(createChat(request.getUserId(), request.getQuery()));
//        }
//
//        // 2. 保存用户消息
//        saveUserMessage(request.getChatId(), request.getChatType(),
//                request.getConversationId(), request.getQuery());
//
//        // 3. 调用Dify发送消息
//        List<FileInfo> files = convertFileInfo(request.getFiles());
//        ChatMessageResponse response = difyService.sendMessage(
//                request.getQuery(),
//                request.getUserId(),
//                request.getConversationId(),
//                files
//        );
//
//        // 4. 保存AI回复
//        saveAssistantMessage(request.getChatId(), request.getChatType(),
//                response.getConversationId(), response.getAnswer());
//
//        return response;
//    }
//
//    /**
//     * 发送消息（流式模式）
//     */
//    @Transactional
//    public SseEmitter sendMessageStream(ChatMessageRequest request) {
//        // 1. 检查是否需要创建新对话
//        if (request.getChatId() == null || request.getChatId().isEmpty()) {
//            request.setChatId(createChat(request.getUserId(), request.getQuery()));
//        }
//
//        // 2. 保存用户消息
//        saveUserMessage(request.getChatId(), request.getChatType(),
//                request.getConversationId(), request.getQuery());
//
//        // 3. 调用Dify发送流式消息
//        List<FileInfo> files = convertFileInfo(request.getFiles());
//        SseEmitter emitter = difyService.sendMessageStream(
//                request.getQuery(),
//                request.getUserId(),
//                request.getConversationId(),
//                files
//        );
//
//        // 注意：流式响应的AI回复需要在前端接收完整后，由前端调用保存接口保存
//        // 或者在DifyService的onMessageEnd回调中保存
//
//        return emitter;
//    }
    
    /**
     * 终止流程
     */
    @Transactional
    public boolean stopConversation(StopConversationRequest request) {
        int rows = chatDetailMapper.updateConversationStopFlag(
                request.getChatId(),
                request.getChatType(),
                request.getConversationId()
        );
        return rows > 0;
    }
    
    /**
     * 停止响应
     */
//    public boolean stopResponse(String taskId) {
//        return difyService.stopResponse(taskId);
//    }
    
    /**
     * 保存消息
     */
    public void saveMessage(ChatDetail detail) {
        chatDetailMapper.insert(detail);
        // 同步到ChatMemory(用于AI上下文)
        /*Message aiMessage = detail.getRole() == MessageRole.USER.name()
                ? new UserMessage(detail.getContent())
                : new AssistantMessage(detail.getContent());
        chatMemory.add(detail.getConversationId(), aiMessage);*/
    }
    

    /**
     * 转换文件信息
     */
//    private List<FileInfo> convertFileInfo(List<FileInfoDTO> fileDTOs) {
//        if (fileDTOs == null || fileDTOs.isEmpty()) {
//            return null;
//        }
//
//        return fileDTOs.stream()
//                .map(dto -> FileInfo.builder()
//                        .type(io.github.imfangs.dify.client.enums.FileType.valueOf(dto.getType()))
//                        .transferMethod(io.github.imfangs.dify.client.enums.FileTransferMethod.valueOf(dto.getTransferMethod()))
//                        .uploadFileId(dto.getUploadFileId())
//                        .build())
//                .collect(Collectors.toList());
//    }
}