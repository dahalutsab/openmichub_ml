export interface Post {
  id?: number;
  title: string;
  content: string;
  images?: string[];
  likesCount?: number;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
}

export interface PostResponse {
  timestamp: string;
  message: string;
  data: Post;
  status: string;
}

export interface PostListResponse {
  timestamp: string;
  message: string;
  data: {
    content: Post[];
    pageable: {
      pageNumber: number;
      pageSize: number;
      offset: number;
      paged: boolean;
      unpaged: boolean;
    };
    last: boolean;
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
    first: boolean;
    numberOfElements: number;
    empty: boolean;
  };
  status: string;
}