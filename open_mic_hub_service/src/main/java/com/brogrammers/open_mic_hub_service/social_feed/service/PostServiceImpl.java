package com.brogrammers.open_mic_hub_service.social_feed.service;

import com.brogrammers.open_mic_hub_service.social_feed.dto.PostRequest;
import com.brogrammers.open_mic_hub_service.social_feed.dto.PostResponse;
import com.brogrammers.open_mic_hub_service.social_feed.entity.PostLike;
import com.brogrammers.open_mic_hub_service.social_feed.entity.Posts;
import com.brogrammers.open_mic_hub_service.social_feed.repository.PostLikeRepository;
import com.brogrammers.open_mic_hub_service.social_feed.repository.PostRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.util.file.FileHandlerUtil;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService{
    private final PostRepository postRepository;
    private final UserInfoRepository userInfoRepository;
    private final PostLikeRepository postLikeRepository;
    private final FileHandlerUtil fileHandlerUtil;
    private final ArtistRepository artistRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    private static final String POST_NOT_FOUND = "Post not found with ID: ";
    @Override
    public PostResponse createPost(PostRequest postRequest) throws IOException {

        Artist artist = loggedInUserUtil.getLoggedInArtist();

        Posts post = new Posts();
        post.setArtist(artist);
        post.setTitle(postRequest.getTitle());
        post.setContent(postRequest.getContent());
        post.setImages(uploadImageInList(postRequest.getImages()));
        post.setLikesCount(0); // Initialize likes count to 0
        postRepository.save(post);

        return new PostResponse(post);
    }

    private List<String> uploadImageInList(List<MultipartFile> images) {
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            for (MultipartFile image : images) {
                String imageUrl = fileHandlerUtil.saveFile(image, "posts").getFileDownloadUri();
                imageUrls.add(imageUrl);
            }
        }
        return imageUrls;
    }

    @Override
    public PostResponse updatePost(Long postId, PostRequest postRequest) throws IOException {
        log.info("Updating post with ID: {}", postId);
        Posts post = requireOwnPost(postId);
        post.setTitle(postRequest.getTitle());
        post.setContent(postRequest.getContent());
        if (postRequest.getImages() != null && !postRequest.getImages().isEmpty()) {
            post.setImages(uploadImageInList(postRequest.getImages()));
        }
        postRepository.save(post);

        return new PostResponse(post);
    }

    @Override
    public PostResponse getPostById(Long postId) {
        log.info("Fetching post with ID: {}", postId);
        return postRepository.findById(postId)
                .map(PostResponse::new)
                .orElseThrow(() -> new RuntimeException(POST_NOT_FOUND + postId));
    }

    @Override
    public void deletePost(Long postId) {
        log.info("Deleting post with ID: {}", postId);
        postRepository.delete(requireOwnPost(postId));
    }

    /**
     * Loads a post and confirms the logged-in artist wrote it.
     *
     * <p>update and delete previously took only an id, so any authenticated caller could edit or
     * delete anyone's post.
     */
    private Posts requireOwnPost(Long postId) {
        Posts post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException(POST_NOT_FOUND + postId));

        Artist loggedInArtist = loggedInUserUtil.getLoggedInArtist();
        if (!post.getArtist().getId().equals(loggedInArtist.getId())) {
            throw new AccessDeniedException("This post is not yours.");
        }
        return post;
    }

    @Override
    public Page<PostResponse> getAllPosts(Pageable pageable) {
        return postRepository.findAllByOrderByCreatedDateDesc(pageable)
                .map(PostResponse::new);
    }

    @Override
    public PostResponse toggleLike(Long postId) {
        Long userId = loggedInUserUtil.getLoggedInUser().getId();
        log.info("Liking post with ID: {} by user ID: {}", postId, userId);
        Posts post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException(POST_NOT_FOUND + postId));
        UserEntity user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        // Assuming PostLike is an entity that represents a like on a post
        PostLike postLike = postLikeRepository.findByPostIdAndUserIdAndIsLikedIsTrue(post.getPostId(), user.getId());
        if (postLike != null) {
            // If the user has already liked the post, toggle the like off
            postLike.setIsLiked(false);
            postLikeRepository.save(postLike);
            log.info("Post with ID: {} was unliked by user ID: {}", postId, userId);

            // Decrement the likes count on the post
            post.setLikesCount(post.getLikesCount() - 1);
            postRepository.save(post);
        } else {
            // Create a new like entry
            postLike = new PostLike();
            postLike.setPostId(postId);
            postLike.setUserId(userId);
            postLike.setIsLiked(true);
            postLikeRepository.save(postLike);
            log.info("Post with ID: {} was liked by user ID: {}", postId, userId);

            // Increment the likes count on the post
            post.setLikesCount(post.getLikesCount() + 1);
            postRepository.save(post);
        }
        return new PostResponse(post);
    }

    @Override
    public Boolean isPostLiked(Long postId) {
        Long userId = loggedInUserUtil.getLoggedInUser().getId();
        log.info("Checking if post with ID: {} is liked by user ID: {}", postId, userId);
        PostLike postLike = postLikeRepository.findByPostIdAndUserIdAndIsLikedIsTrue(postId, userId);
        return postLike != null && postLike.getIsLiked();
    }


}
