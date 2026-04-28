import 'package:flutter/material.dart';

class ReviewImageSlider extends StatefulWidget {
  final int imageCount;

  const ReviewImageSlider({
    super.key,
    required this.imageCount,
  });

  @override
  State<ReviewImageSlider> createState() => _ReviewImageSliderState();
}

class _ReviewImageSliderState extends State<ReviewImageSlider> {
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const Color mainBlue = Color(0xFF2260FF);
    const Color softBg = Color(0xFFECF1FF);
    const Color gray = Color(0xFF7E7E7E);

    return Scrollbar(
      controller: _scrollController,
      thumbVisibility: true,
      radius: const Radius.circular(20),
      thickness: 4,
      child: SingleChildScrollView(
        controller: _scrollController,
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 12),
        child: Row(
          children: List.generate(
            widget.imageCount,
            (index) => Container(
              width: 140,
              height: 95,
              margin: const EdgeInsets.only(right: 8),
              decoration: BoxDecoration(
                color: softBg,
                borderRadius: BorderRadius.circular(4),
              ),
              child: const Center(
                child: Icon(
                  Icons.image_outlined,
                  size: 26,
                  color: gray,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}