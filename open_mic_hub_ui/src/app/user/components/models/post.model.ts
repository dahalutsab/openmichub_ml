// models/post.model.ts
export interface Post {
  id: number;
  title: string;
  content: string;
  images: string[];
  likesCount: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  author: string;
  authorProfileImage: string;
}

export interface PostsResponse {
  timestamp: string;
  message: string;
  data: {
    content: Post[];
    pageable: {
      pageNumber: number;
      pageSize: number;
      sort: {
        empty: boolean;
        sorted: boolean;
        unsorted: boolean;
      };
      offset: number;
      paged: boolean;
      unpaged: boolean;
    };
    last: boolean;
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
    sort: {
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    first: boolean;
    numberOfElements: number;
    empty: boolean;
  };
  status: string;
}

export interface LikeToggleResponse {
  timestamp: string;
  message: string;
  data: Post;
  status: string;
}