package org.web03.service;

import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.*;
//import org.web03.pojo.HistoryResult;

import java.util.List;
import java.util.Map;


public interface MessageService {


    SendPrivateResult sendPrivate(String myUserId, PrivateMessageRequest req);

    HistoryResult history(String myUserId, String peerId, Integer page, Integer size);

    List<ConversationResult> conversations(String myUserId);

    MessagesFile sendFile(String myUserId, MultipartFile file, String receiverId);

    void markRead(String currentUserId, String peerId);
}
