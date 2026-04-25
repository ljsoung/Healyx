import 'package:flutter/material.dart';

// (참고) LoginRequiredDialog import는 프로젝트 구조에 맞게 유지해주세요.
// import '../../dialogs/login_required_dialog.dart';

class FindHospitalDetailScreen extends StatefulWidget {
  const FindHospitalDetailScreen({
    super.key,
    required this.hasReview,
    required this.hasBadge,
    required this.isLoggedIn,
  });

  // true = 리뷰 있음 화면 / false = 리뷰 없음 화면
  final bool hasReview;

  // 병원찾기 리스트에서 전달받는 배지 여부
  // true = 인증 배지 표시 / false = 배지 숨김
  final bool hasBadge;

  // 병원찾기 결과 화면에서 전달받은 로그인 여부
  // false = 비로그인(게스트), true = 로그인 사용자
  final bool isLoggedIn;

  @override
  State<FindHospitalDetailScreen> createState() => _FindHospitalDetailScreenState();
}

class _FindHospitalDetailScreenState extends State<FindHospitalDetailScreen> {
  // --- 색상 정의 ---
  final Color mainBlue = const Color(0xFF2260FF);
  final Color lightBlue = const Color(0xFFCAD6FF);
  final Color softBg = const Color(0xFFECF1FF);
  final Color lineColor = const Color(0xFF4378FF);
  final Color greyColor = const Color(0xFF7E7E7E);

  // --- 더미 데이터 설정 ---
  // 실제 서버 연동 시 리뷰 목록(List) 받아와 반복 출력 예정
  final List<ReviewData> _reviewList = [
    ReviewData(
      nickname: '닉네임123',
      content: '의사 선생님이 친절하고 시설이 깨끗해요',
      rating: '5',
      hasImages: false,
    ),
    // image_1.png 디자인을 적용할 이미지 리뷰
    ReviewData(
      nickname: '닉네임456',
      content:
          '무엇보다 병원은 진료 자체는 매우 만족스러웠습니다. 제공받은 안내도 친절했고, 직원분들도 외국인 환자에게 설명을 잘해줬습니다.',
      rating: '3',
      hasImages: true,
      imageCount: 4, // 이미지가 여러 개일 때 스크롤바 확인용
    ),
    ReviewData(
      nickname: '닉네임789',
      content: '대기 시간이 조금 있었지만 안내가 잘 되어 있어서 이용하기 편했습니다.',
      rating: '4',
      hasImages: false,
    ),
    // 또 다른 이미지 리뷰
    ReviewData(
      nickname: '닉네임101',
      content:
          '접수부터 진료까지 전반적으로 깔끔했고, 필요한 설명을 차분하게 해주셔서 좋았습니다.',
      rating: '5',
      hasImages: true,
      imageCount: 3,
    ),
    ReviewData(
      nickname: '닉네임202',
      content: '시설이 깨끗하고 위치도 찾기 쉬웠습니다. 다음에도 이용할 것 같아요.',
      rating: '4',
      hasImages: false,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context),
            Expanded(
              child: Stack(
                children: [
                  SingleChildScrollView(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(24, 18, 24, 0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildHospitalSummary(),
                          const SizedBox(height: 26),
                          _buildHospitalInfo(),
                          const SizedBox(height: 20),
                          _buildReviewHeader(context),
                          const SizedBox(height: 420), // 하단 시트 공간 확보
                        ],
                      ),
                    ),
                  ),

                  // 리뷰 존재 여부에 따라 하단 UI만 다르게 표시
                  widget.hasReview
                      ? _buildReviewSheet()
                      : _buildEmptyReviewSheet(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // --- 위젯 빌드 메서드들 ---

  Widget _buildHeader(BuildContext context) {
    return SizedBox(
      height: 86,
      child: Stack(
        children: [
          Positioned(
            left: 8,
            top: 16,
            child: IconButton(
              onPressed: () => Navigator.pop(context),
              icon: Icon(
                Icons.arrow_back_ios_new,
                color: mainBlue,
                size: 21,
              ),
            ),
          ),
          Center(
            child: Text(
              '병원 찾기',
              style: TextStyle(
                color: mainBlue,
                fontSize: 22,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHospitalSummary() {
    return SizedBox(
      width: double.infinity,
      child: Stack(
        children: [
          // 병원찾기 리스트에서 전달받는 배지 값이 true일 때만 표시
          if (widget.hasBadge)
            Positioned(
              right: 0,
              top: 0,
              child: CircleAvatar(
                radius: 16,
                backgroundColor: mainBlue,
                child: const Icon(
                  Icons.workspace_premium_outlined,
                  color: Colors.white,
                  size: 18,
                ),
              ),
            ),

          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                widget.hasReview ? '00병원' : '&&병원',
                style: TextStyle(
                  color: mainBlue,
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 10),
              const Text(
                '서울특별시 송파구 올림픽로 43길 88',
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.black87,
                ),
              ),
              const SizedBox(height: 10),
              _buildRatingChip(widget.hasReview ? '4.8' : ''),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildRatingChip(String rating) {
    return Container(
      width: rating.isEmpty ? 52 : 62,
      height: 24,
      decoration: BoxDecoration(
        color: lightBlue,
        borderRadius: BorderRadius.circular(15),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.star_border,
            color: mainBlue,
            size: 14,
          ),
          if (rating.isNotEmpty) ...[
            const SizedBox(width: 3),
            Text(
              rating,
              style: TextStyle(
                color: mainBlue,
                fontSize: 11,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildHospitalInfo() {
    return Column(
      children: [
        Divider(color: lineColor, thickness: 1),
        const SizedBox(height: 16),
        _infoRow('병원 타입', widget.hasReview ? '대학병원' : '의원'),
        const SizedBox(height: 16),
        _infoRow('전화번호', '02-0000-0000'),
        const SizedBox(height: 18),
        Divider(color: lineColor, thickness: 1),
      ],
    );
  }

  Widget _infoRow(String label, String value) {
    return Row(
      children: [
        Text(
          label,
          style: TextStyle(
            color: mainBlue,
            fontSize: 12,
            fontWeight: FontWeight.w500,
          ),
        ),
        const Spacer(),
        Text(
          value,
          style: const TextStyle(
            color: Colors.black,
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }

  Widget _buildReviewHeader(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        Text(
          widget.hasReview
              ? '${_reviewList.length}개의 리뷰' // 실제 데이터 수 표시
              : '0개의 리뷰',
          style: const TextStyle(
            color: Colors.black,
            fontSize: 13,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(width: 18),
        SizedBox(
          height: 34,
          width: 104,
          child: ElevatedButton(
            onPressed: () {
              if (!widget.isLoggedIn) {
                _showLoginRequiredDialog(context);
                return;
              }

              // TODO: 리뷰 작성 화면 연결 예정
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: mainBlue,
              elevation: 2,
              padding: EdgeInsets.zero,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
            ),
            child: const Text(
              '리뷰쓰기',
              maxLines: 1,
              style: TextStyle(
                color: Colors.white,
                fontSize: 13,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
      ],
    );
  }

  // --- 하단 시트 빌드 메서드들 ---

  Widget _buildReviewSheet() {
    return DraggableScrollableSheet(
      initialChildSize: 0.48,
      minChildSize: 0.34,
      maxChildSize: 0.88,
      builder: (context, controller) {
        return Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(12, 14, 12, 14),
          decoration: BoxDecoration(
            color: lightBlue,
            borderRadius: const BorderRadius.vertical(
              top: Radius.circular(22),
            ),
          ),
          child: ListView.separated(
            controller: controller,
            itemCount: _reviewList.length + 1, // 헤더(바) 포함
            separatorBuilder: (context, index) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              if (index == 0) {
                // 상단 손잡이 바
                return Center(
                  child: Container(
                    width: 42,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 14),
                    decoration: BoxDecoration(
                      color: const Color(0xFFE6E6E6),
                      borderRadius: BorderRadius.circular(20),
                    ),
                  ),
                );
              }
              // 리뷰 카드 표시 (index 1부터 실제 데이터)
              final review = _reviewList[index - 1];
              return ReviewCard(review: review);
            },
          ),
        );
      },
    );
  }

  Widget _buildEmptyReviewSheet() {
    return DraggableScrollableSheet(
      initialChildSize: 0.48,
      minChildSize: 0.34,
      maxChildSize: 0.88,
      builder: (context, controller) {
        return Container(
          width: double.infinity,
          decoration: BoxDecoration(
            color: lightBlue,
            borderRadius: const BorderRadius.vertical(
              top: Radius.circular(22),
            ),
          ),
          child: ListView(
            controller: controller,
            children: [
              const SizedBox(height: 14),
              Center(
                child: Container(
                  width: 42,
                  height: 4,
                  decoration: BoxDecoration(
                    color: const Color(0xFFE6E6E6),
                    borderRadius: BorderRadius.circular(20),
                  ),
                ),
              ),
              SizedBox(
                height: 280,
                child: Center(
                  child: Text(
                    '첫번째 리뷰를 써보세요.',
                    style: TextStyle(
                      color: mainBlue,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  // --- 공통 메서드 ---

  void _showLoginRequiredDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('로그인이 필요합니다'),
          content: const Text('리뷰를 작성하려면 로그인을 해주세요.'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('확인'),
            ),
          ],
        );
      },
    );
  }
}

// --- 리뷰 데이터 클래스 ---
class ReviewData {
  final String nickname;
  final String content;
  final String rating;
  final bool hasImages;
  final int imageCount;

  ReviewData({
    required this.nickname,
    required this.content,
    required this.rating,
    this.hasImages = false,
    this.imageCount = 0,
  });
}

// --- 개별 리뷰 카드 위젯 ( StatefulWidget) ---
class ReviewCard extends StatefulWidget {
  final ReviewData review;

  const ReviewCard({super.key, required this.review});

  @override
  State<ReviewCard> createState() => _ReviewCardState();
}

class _ReviewCardState extends State<ReviewCard> {
  // 스크롤바와 스크롤 위치를 연결하기 위한 컨트롤러
  late final ScrollController _imageScrollController;

  final Color mainBlue = const Color(0xFF2260FF);
  final Color softBg = const Color(0xFFECF1FF);
  final Color greyColor = const Color(0xFF7E7E7E);

  @override
  void initState() {
    super.initState();
    _imageScrollController = ScrollController();
  }

  @override
  void dispose() {
    _imageScrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        boxShadow: const [
          BoxShadow(
            color: Color.fromRGBO(0, 0, 0, 0.12),
            blurRadius: 5,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildNickname(widget.review.nickname),
          const SizedBox(height: 5),
          _buildContent(widget.review.content),
          const SizedBox(height: 6),
          _buildSmallRating(widget.review.rating),

          // 이미지가 있는 리뷰일 때만 하단 이미지 및 스크롤바 표시
          if (widget.review.hasImages) ...[
            const SizedBox(height: 12),
            _buildImageScrollArea(),
          ],
        ],
      ),
    );
  }

  Widget _buildNickname(String nickname) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: softBg,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        nickname,
        style: TextStyle(
          color: mainBlue,
          fontSize: 12,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }

  Widget _buildContent(String content) {
    return Text(
      content,
      style: const TextStyle(
        color: Colors.black87,
        fontSize: 11,
        height: 1.35,
      ),
    );
  }

  Widget _buildSmallRating(String rating) {
    return Container(
      width: 44,
      height: 20,
      decoration: BoxDecoration(
        color: softBg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.star_border, color: mainBlue, size: 12),
          const SizedBox(width: 2),
          Text(
            rating,
            style: TextStyle(
              color: mainBlue,
              fontSize: 10,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }

  // --- 핵심 수정 영역: 기본 스크롤바가 적용된 이미지 영역 ---
  Widget _buildImageScrollArea() {
    // 테마 설정을 통해 스크롤바를 image_1.png 디자인처럼 커스텀
    return Theme(
      data: Theme.of(context).copyWith(
        scrollbarTheme: ScrollbarThemeData(
          // image_1.png의 파란색 둥근 핸들 설정
          thumbColor: WidgetStateProperty.all(mainBlue),
          radius: const Radius.circular(20),
          thickness: WidgetStateProperty.all(4.0), // 핸들 두께
          
          // image_1.png의 연한 회색 트랙(바닥) 설정
          trackColor: WidgetStateProperty.all(const Color(0xFFE6E6E6)),
          trackBorderColor: WidgetStateProperty.all(Colors.transparent),
          minThumbLength: 20.0, // 핸들의 최소 길이
        ),
      ),
      // 기본 Scrollbar 위젯 사용
      child: Scrollbar(
        controller: _imageScrollController,
        // *** image_1.png처럼 핸들과 트랙을 항상 보이게 하는 핵심 속성 ***
       // isAlwaysShown: true, // 핸들 고정
       // showTrackOnHover: true, // 트랙 고정 (마우스 호버 시 트랙 보이게)
        
        child: SingleChildScrollView(
          controller: _imageScrollController,
          scrollDirection: Axis.horizontal,
          physics: const BouncingScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 12), // 스크롤바와 이미지 사이 간격
          child: Row(
            children: List.generate(
              widget.review.imageCount,
              (index) => Padding(
                padding: const EdgeInsets.only(right: 8.0),
                child: _imageBox(),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _imageBox() {
    return Container(
      width: 140,
      height: 95,
      decoration: BoxDecoration(
        color: softBg,
        borderRadius: BorderRadius.circular(4),
      ),
      child: const Center(
        child: Icon(
          Icons.image_outlined,
          size: 26,
          color: Color(0xFF7E7E7E),
        ),
      ),
    );
  }
}