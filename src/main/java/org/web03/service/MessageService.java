package org.web03.service;

import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.ConversationResult;
//import org.web03.pojo.HistoryResult;
import org.web03.pojo.HistoryResult;
import org.web03.pojo.PrivateMessageRequest;
import org.web03.pojo.SendPrivateResult;

import java.util.List;
import java.util.Map;


public interface MessageService {


    SendPrivateResult sendPrivate(String myUserId, PrivateMessageRequest req);

    HistoryResult history(String myUserId, String peerId, Integer page, Integer size);

    List<ConversationResult> conversations(String myUserId);

    Map<String, Object> sendFile(String myUserId, MultipartFile file, String receiverId);

    void markRead(String currentUserId, String peerId);
}
