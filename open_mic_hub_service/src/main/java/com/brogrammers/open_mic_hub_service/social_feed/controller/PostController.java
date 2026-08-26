package com.brogrammers.open_mic_hub_service.social_feed.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.social_feed.dto.PostRequest;
import com.brogrammers.open_mic_hub_service.social_feed.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
public class PostController extends BaseController {
    private final PostService postService;

    @PreAuthorize("hasRole('ARTIST')")
    @PostMapping
    public ResponseEntity<GlobalApiResponse> createPost(@ModelAttribute PostRequest postRequest) throws IOException {
        return successResponse(postService.createPost(postRequest), "Post created successfully");
    }

    @GetMapping("/{postId}")
    public ResponseEntity<GlobalApiResponse> getPostById(@PathVariable Long postId) {
        return successResponse(postService.getPostById(postId), "Post retrieved successfully");
    }

    @PutMapping("/{postId}")
    public ResponseEntity<GlobalApiResponse> updatePost(@PathVariable Long postId, @ModelAttribute PostRequest postRequest) throws IOException {
        return successResponse(postService.updatePost(postId, postRequest), "Post updated successfully");
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<GlobalApiResponse> deletePost(@PathVariable Long postId) {
        postService.deletePost(postId);
        return successResponse(null, "Post deleted successfully");
    }

    @PutMapping("/toggle-like/{postId}")
    public ResponseEntity<GlobalApiResponse> toggleLike(@PathVariable Long postId) {
        return successResponse(postService.toggleLike(postId), "Post like toggled successfully");
    }

    @GetMapping
    public ResponseEntity<GlobalApiResponse> getAllPosts(Pageable pageable) {
        return successResponse(postService.getAllPosts(pageable), "Posts retrieved successfully");
    }

    @GetMapping("/is-liked/{postId}")
    public ResponseEntity<GlobalApiResponse> isPostLiked(@PathVariable Long postId) {
        return successResponse(postService.isPostLiked(postId), "Post like status retrieved successfully");
    }

}
