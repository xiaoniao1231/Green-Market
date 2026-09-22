package org.web03.service;

import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.Messages.ConversationResult;
import org.web03.pojo.Messages.HistoryResult;
import org.web03.pojo.Messages.MessagesFile;
import org.web03.pojo.Messages.PrivateMessageRequest;
import org.web03.pojo.Messages.SendPrivateResult;
//import org.web03.pojo.Messages.HistoryResult;

import java.util.List;


public interface MessageService {


    SendPrivateResult sendPrivate(String myUserId, PrivateMessageRequest req);

    HistoryResult history(String myUserId, String peerId, Integer page, Integer size);

    List<ConversationResult> conversations(String myUserId);

    MessagesFile sendFile(String myUserId, MultipartFile file, String receiverId);

    void markRead(String currentUserId, String peerId);
}
