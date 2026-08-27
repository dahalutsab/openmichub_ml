package com.brogrammers.open_mic_hub_service.social_feed.service;

import com.brogrammers.open_mic_hub_service.social_feed.dto.PostRequest;
import com.brogrammers.open_mic_hub_service.social_feed.dto.PostResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;

public interface PostService {
    PostResponse createPost(PostRequest postRequest) throws IOException;
    PostResponse updatePost(Long postId, PostRequest postRequest) throws IOException;
    PostResponse getPostById(Long postId);
    void deletePost(Long postId);
    Page<PostResponse> getAllPosts(Pageable pageable);

    /** One artist's posts, newest first. Readable without an account: profiles are public. */
    Page<PostResponse> getPostsByArtist(Long artistId, Pageable pageable);
    PostResponse toggleLike(Long postId);
    Boolean isPostLiked(Long postId);
}
