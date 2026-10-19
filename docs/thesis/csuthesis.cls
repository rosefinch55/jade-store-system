%
% 本模版根据中南大学本科生学位论文撰写规范创建
% 参考源：http://gra.its.csu.edu.cn/yjsy/pygl/wjtzxq54858_3_6.html
% 论文内容一般应由十一个主要部分组成，依次为：
% 1.封面
% 2.中文摘要；
% 3.英文摘要；
% 4.目录；
% 5.符号说明（必要时）；
% 6.论文正文；
% 7.参考文献；
% 8.致谢。
%
%
% 重构
%
% 原因：原有模板缺乏维护，模板文件(CSUthesis.cls)缺少注释解释what和why，缺乏适配各种情形（科研报告、本、硕、博论文）的模板选项。
% 需求：有越来越多20届和21届应届毕业生需要一份“开箱可用”的学术论文模板（学士、硕士、博士）
%
% 参考本校多位学长（包括最初的模板作者郭大侠）编写的模板，以及SJTU Thesis和WHU Thesis Latex模板进行重构。

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% 声明部分（Identification）
%
% 指定模板使用的 Latex 版本，标识输出模板名
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{csuthesis}[2020/01/21 v1.4.9-dev by Edwardzcn edwardzcn98@gmail.com]


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% 预先定义（Preliminary declarations）
% 
% 定义kv对，在模板文件中使用的指令并引入依赖的包
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


% 定义一些命令用于写文档
% /cs 在ltxdoc 包中也提供，剩下是自定义的
% 借鉴自thuthesis和ustcthesis模板
\DeclareRobustCommand\cs[1]{\texttt{\char`\\#1}}
\DeclareRobustCommand\file{\nolinkurl}
\DeclareRobustCommand\env{\textsf}
\DeclareRobustCommand\pkg{\textsf}
\DeclareRobustCommand\cls{\textsf}

% 模板类型选取
\newif\ifcsu@type@bachelor
\csu@type@bachelortrue
\newif\ifcsu@type@master
\csu@type@masterfalse
\newif\ifcsu@type@doctor
\csu@type@doctorfalse
\newif\ifcsu@type@course
\csu@type@coursefalse
\newif\ifcsu@type@print
\csu@type@printfalse
\newif\ifcsu@type@graduate
\csu@type@graduatefalse
\ifcsu@type@master
    \csu@type@graduatetrue
\fi
\ifcsu@type@doctor
    \csu@type@graduatetrue
\fi

% 定义致谢环境，盲审下隐藏致谢
\newif\ifcsu@review
\csu@reviewfalse

% 定义模板选项
\DeclareOption{doctor}{\csu@type@doctortrue}
\DeclareOption{master}{\csu@type@mastertrue}
\DeclareOption{bachelor}{\csu@type@bachelortrue}
\DeclareOption{course}{\csu@type@coursetrue}
\DeclareOption{forprint}{\csu@type@printtrue}
% 使用 \XeTeX{} 引擎时，\pkg{fontspec} 宏包会被 \pkg{xeCJK} 自动调用。传递给
% \pkg{fontspec} 宏包 \opt{no-math} 选项，避免部分数学符号字体自动调整为 CMR。
% 并使用 \opt{quiet} 忽略警告。
%    \begin{macrocode}
% \PassOptionsToPackage{no-math,quiet}{fontspec}
\PassOptionsToPackage{no-math}{fontspec}

\DeclareOption*{\PassOptionsToClass{\CurrentOption}{article}}
\ProcessOptions\relax


% 读取基类
% \LoadClass[a4paper,12pt]{article}
\LoadClass[UTF8,openany,a4paper,oneside,zihao=-4]{ctexbook}
% 注意openany和oneside参数，默认是无空白页不区分双面印。

% 加载宏包
% 引擎执行判断宏包
\RequirePackage{ifxetex}
% 加载xparse宏包
\RequirePackage{xparse}
% 报错与警告
\NewDocumentCommand{\csu@error}{ m o }{
    \ClassError{csuthesis_error}{#1}{#2}
}
\NewDocumentCommand{\csu@warning}{m o }{
    \ClassWarning{csuthesis_warning}{#1}{#2}
}

\RequireXeTeX
\ifxetex
    % Pass
\else
    \csu@error{Please use xelatex driver instead of pdflatex.}
\fi

% 支持中文的 ctex 宏包
\RequirePackage{ctex}
% 页面布局
\RequirePackage{geometry}
% 使用 \pkg{amsmath} 处理数学公式
\RequirePackage{amsmath}
% 下面的数学宏包和unicode-math 冲突
% \RequirePackage{amsfonts}
% \RequirePackage{amssymb}
% \RequirePackage{bm}
% 使用 \pkg{unicode-math} 处理数学字体
\RequirePackage{unicode-math}
% 算法排版宏包
\RequirePackage[chapter]{algorithm}
\RequirePackage{algorithmic}
\floatname{algorithm}{算法}

% 自定义关键词
\RequirePackage{pgfkeys}
% 设置目录
\RequirePackage{titletoc}
% 设置字体
\RequirePackage{fontenc}
% 设置颜色
\RequirePackage{xcolor}
% 设置页眉和页脚
\RequirePackage{fancyhdr}
% 代码高亮
% 注：该包依赖python环境Pygments语法高亮显示工具包
% 并且需要 -shell-escape参数
% TODO: 日后需要修改为可配置

% 超链接 hyperref 的设置
% 提供书签与链接
\RequirePackage{hyperref}
% 插入图片
\RequirePackage{graphicx}
% 表格
\RequirePackage{array}
% 长表格
\RequirePackage{longtable}
% booktabs 提供了\toprule 等命令.
\RequirePackage{booktabs}
% multirow 支持在表格中跨行
\RequirePackage{multirow}
% 调整间隔, 让表格更好看些
\RequirePackage{bigstrut}
%在跨行表格中输入定界符
\RequirePackage{bigdelim}
% 保护脆弱命令
\RequirePackage{cprotect}
% 设置代码高亮
% \RequirePackage{minted}
% 设置代码环境
\RequirePackage{listings}
\lstset{
 breaklines,
 columns=fixed,       
 numbers=none,                                        % 在左侧显示行号
 numberstyle=\tiny\color{gray},                       % 设定行号格式
 frame=single,                                        % 不显示背景边框
 rulecolor=\color{black},                             % if not set, the frame-color may be changed on line-breaks within not-black text (e.g. commens (green here))
%  backgroundcolor=\color[RGB]{245,245,244},            % 设定背景颜色
 keywordstyle=\color[RGB]{40,40,255},                 % 设定关键字颜色
 numberstyle=\footnotesize\color{darkgray},           
 commentstyle=\it\color[RGB]{0,96,96},                % 设置代码注释的格式
 stringstyle=\rmfamily\slshape\color[RGB]{128,0,0},   % 设置字符串格式
 showstringspaces=false,                              % 不显示字符串中的空格
 language=c++,                                        % 设置语言
 aboveskip=20pt
}


% jing: ccaption宏包不能出现在 caption 宏包之后
% 设置浮动体的标题
\RequirePackage[justification=centering]{caption}
\RequirePackage[justification=centering]{subcaption}
% 定制列表环境
\RequirePackage{enumitem}
% 提供\AtBeginEnvironment以方便全局调整一些结构的设置
\RequirePackage{etoolbox}
% 确定宏定义的位置
\RequirePackage{filehook}
% 枚举
\RequirePackage{enumitem}
% 末尾页
\RequirePackage{lastpage}
% 
% \RequirePackage{hypdoc}

% 参考文献格式 GB/T7714-2015
% 来自https://github.com/hushidong/biblatex-gb7714-2015
\RequirePackage[backend=biber,gbpub=false, style=gb7714-2015]{biblatex}

\AtEndOfClass{
% 根据模板类型加载不同配置
\ifcsu@type@graduate
    \input{graduate.cls}
\else
    \input{undergraduate.cls}
    % \input{test.cls}
\fi
}


% 字体配置
\let\sjtu@font@family@xits\@empty
\newcommand\sjtu@font@set@xits@names{%
  \ifx\sjtu@font@family@xits\@empty
    \IfFontExistsTF{XITSMath-Regular.otf}{%
      \gdef\sjtu@font@family@xits{XITS}%
      \gdef\sjtu@font@style@xits@rm{Regular}%
      \gdef\sjtu@font@style@xits@bf{Bold}%
      \gdef\sjtu@font@style@xits@it{Italic}%
      \gdef\sjtu@font@style@xits@bfit{BoldItalic}%
      \gdef\sjtu@font@name@xits@math@rm{XITSMath-Regular}%
      \gdef\sjtu@font@name@xits@math@bf{XITSMath-Bold}%
    }{%
      \gdef\sjtu@font@family@xits{xits}%
      \gdef\sjtu@font@style@xits@rm{regular}%
      \gdef\sjtu@font@style@xits@bf{bold}%
      \gdef\sjtu@font@style@xits@it{italic}%
      \gdef\sjtu@font@style@xits@bfit{bolditalic}%
      \gdef\sjtu@font@name@xits@math@rm{xits-math}%
      \gdef\sjtu@font@name@xits@math@bf{xits-mathbold}%
    }%
  \fi
}
\let\sjtu@font@family@libertinus\@empty
\newcommand\sjtu@font@set@libertinus@names{%
  \ifx\sjtu@font@family@libertinus\@empty
    \IfFontExistsTF{LibertinusSerif-Regular.otf}{%
      \gdef\sjtu@font@family@libertinus@serif{LibertinusSerif}%
      \gdef\sjtu@font@family@libertinus@sans{LibertinusSans}%
      \gdef\sjtu@font@name@libertinus@math{LibertinusMath-Regular}%
      \gdef\sjtu@font@style@libertinus@rm{Regular}%
      \gdef\sjtu@font@style@libertinus@bf{Bold}%
      \gdef\sjtu@font@style@libertinus@it{Italic}%
      \gdef\sjtu@font@style@libertinus@bfit{BoldItalic}%
    }{%
      \gdef\sjtu@font@family@libertinus@serif{libertinusserif}%
      \gdef\sjtu@font@family@libertinus@sans{libertinussans}%
      \gdef\sjtu@font@name@libertinus@math{libertinusmath-regular}%
      \gdef\sjtu@font@style@libertinus@rm{regular}%
      \gdef\sjtu@font@style@libertinus@bf{bold}%
      \gdef\sjtu@font@style@libertinus@it{italic}%
      \gdef\sjtu@font@style@libertinus@bfit{bolditalic}%
    }%
  \fi
}
\newcommand\sjtu@set@font@xits{%
  \sjtu@font@set@xits@names
  \setmainfont{\sjtu@font@family@xits}[
    Extension      = .otf,
    UprightFont    = *-\sjtu@font@style@xits@rm,
    BoldFont       = *-\sjtu@font@style@xits@bf,
    ItalicFont     = *-\sjtu@font@style@xits@it,
    BoldItalicFont = *-\sjtu@font@style@xits@bfit,
  ]
}
\newcommand\sjtu@set@font@times{%
  \setmainfont{Times New Roman}[Ligatures = Rare]
  \setsansfont{Arial}
  \setmonofont{Courier New}[Scale = MatchLowercase]
}
\newcommand\sjtu@set@font@stix{%
  \setmainfont{STIX2Text}[
    Extension      = .otf,
    UprightFont    = *-Regular,
    BoldFont       = *-Bold,
    ItalicFont     = *-Italic,
    BoldItalicFont = *-BoldItalic,
  ]
}
\newcommand\sjtu@set@font@step{%
  \setmainfont{STEP}[
    Extension      = .otf,
    UprightFont    = *-Regular,
    BoldFont       = *-Bold,
    ItalicFont     = *-Italic,
    BoldItalicFont = *-BoldItalic,
  ]
}
\newcommand\sjtu@set@font@source@sans@mono{%
  \setsansfont{SourceSansPro}[
    Extension      = .otf,
    UprightFont    = *-Regular ,
    ItalicFont     = *-RegularIt ,
    BoldFont       = *-Bold ,
    BoldItalicFont = *-BoldIt,
  ]
  \setmonofont{SourceCodePro}[
    Extension      = .otf,
    UprightFont    = *-Regular ,
    ItalicFont     = *-RegularIt ,
    BoldFont       = *-Bold ,
    BoldItalicFont = *-BoldIt,
    Scale          = MatchLowercase,
  ]
}
\newcommand\sjtu@set@font@termes{%
  \setmainfont{texgyretermes}[
    Extension      = .otf,
    UprightFont    = *-regular,
    BoldFont       = *-bold,
    ItalicFont     = *-italic,
    BoldItalicFont = *-bolditalic,
  ]%
}
\newcommand\sjtu@set@font@pagella{%
  \setmainfont{texgyrepagella}[
    Extension      = .otf,
    UprightFont    = *-regular,
    BoldFont       = *-bold,
    ItalicFont     = *-italic,
    BoldItalicFont = *-bolditalic,
  ]%
}
\newcommand\sjtu@set@font@texgyre@sans@mono{%
  \setsansfont{texgyreheros}[
    Extension      = .otf,
    UprightFont    = *-regular,
    BoldFont       = *-bold,
    ItalicFont     = *-italic,
    BoldItalicFont = *-bolditalic,
  ]%
  \setmonofont{texgyrecursor}[
    Extension      = .otf,
    UprightFont    = *-regular,
    BoldFont       = *-bold,
    ItalicFont     = *-italic,
    BoldItalicFont = *-bolditalic,
  ]%
}
\newcommand\sjtu@set@font@cambria{%
  \setmainfont{Cambria}
  \setsansfont{Calibri}
  \setmonofont{Consolas}[Scale = MatchLowercase]
}
\newcommand\sjtu@set@font@libertinus{%
  \sjtu@font@set@libertinus@names
  \setmainfont{\sjtu@font@family@libertinus@serif}[
    Extension      = .otf,
    UprightFont    = *-\sjtu@font@style@libertinus@rm,
    BoldFont       = *-\sjtu@font@style@libertinus@bf,
    ItalicFont     = *-\sjtu@font@style@libertinus@it,
    BoldItalicFont = *-\sjtu@font@style@libertinus@bfit,
  ]%
  \setsansfont{\sjtu@font@family@libertinus@sans}[
    Extension      = .otf,
    UprightFont    = *-\sjtu@font@style@libertinus@rm,
    BoldFont       = *-\sjtu@font@style@libertinus@bf,
    ItalicFont     = *-\sjtu@font@style@libertinus@it,
  ]%
  \setmonofont{lmmonolt10}[
    Extension      = .otf,
    UprightFont    = *-regular,
    BoldFont       = *-bold,
    ItalicFont     = *-oblique,
    BoldItalicFont = *-boldoblique,
  ]%
}
\newcommand\sjtu@set@font@lm{%
  \setmainfont{lmroman10}[
    Extension      = .otf,
    UprightFont    = *-regular,
    BoldFont       = *-bold,
    ItalicFont     = *-italic,
    BoldItalicFont = *-bolditalic,
  ]%
  \setsansfont{lmsans10}[
    Extension      = .otf,
    UprightFont    = *-regular,
    BoldFont       = *-bold,
    ItalicFont     = *-oblique,
    BoldItalicFont = *-boldoblique,
  ]%
  \setmonofont{lmmonolt10}[
    Extension      = .otf,
    UprightFont    = *-regular,
    BoldFont       = *-bold,
    ItalicFont     = *-oblique,
    BoldItalicFont = *-boldoblique,
  ]%
}
% 使用 \pkg{unicode-math} 配置数学字体。
\unimathsetup{
  math-style = ISO,
  bold-style = ISO,
  nabla      = upright,
  partial    = upright,
}
\newcommand\sjtu@set@math@font@xits{%
  \sjtu@font@set@xits@names
  \setmathfont{\sjtu@font@name@xits@math@rm}[
    Extension    = .otf,
    BoldFont     = \sjtu@font@name@xits@math@bf,
    StylisticSet = 8,
  ]%
  \setmathfont{\sjtu@font@name@xits@math@rm}[
    Extension    = .otf,
    BoldFont     = \sjtu@font@name@xits@math@bf,
    StylisticSet = 1,
    range        = {cal,bfcal},
  ]%
}
\newcommand\sjtu@set@math@font@stix{%
  \setmathfont{STIX2Math}[
    Extension    = .otf,
    StylisticSet = 8,
  ]%
  \setmathfont{STIX2Math}[
    Extension    = .otf,
    StylisticSet = 1,
    range        = {cal,bfcal},
  ]%
}
\newcommand\sjtu@set@math@font@step{%
  \setmathfont{STEPMath-Regular}[
    Extension    = .otf,
    BoldFont     = STEPMath-Bold,
    StylisticSet = 8,
  ]%
  \setmathfont{STEPMath-Regular}[
    Extension    = .otf,
    BoldFont     = STEPMath-Bold,
    StylisticSet = 1,
    range        = {cal,bfcal},
  ]%
}
\newcommand\sjtu@set@math@font@termes{%
  \setmathfont{texgyretermes-math.otf}
}
\newcommand\sjtu@set@math@font@pagella{%
  \setmathfont{texgyrepagella-math.otf}
}
\newcommand\sjtu@set@math@font@cambria{%
  \setmathfont{Cambria Math}
}
\newcommand\sjtu@set@math@font@libertinus{%
  \sjtu@font@set@libertinus@names
  \setmathfont{\sjtu@font@name@libertinus@math .otf}%
}
\newcommand\sjtu@set@math@font@lm{%
  \setmathfont{latinmodern-math.otf}%
}
%    \end{macrocode}
%
% 设置西文字体集。
%    \begin{macrocode}
\newcommand\sjtu@load@fontset@xits{%
  \sjtu@set@font@xits
  \sjtu@set@font@source@sans@mono
  \sjtu@set@math@font@xits
}
\newcommand\sjtu@load@fontset@times{%
  \sjtu@set@font@times
  \sjtu@set@math@font@xits
}
\newcommand\sjtu@load@fontset@stix{%
  \sjtu@set@font@stix
  \sjtu@set@font@source@sans@mono
  \sjtu@set@math@font@stix
}
\newcommand\sjtu@load@fontset@step{%
  \sjtu@set@font@step
  \sjtu@set@font@source@sans@mono
  \sjtu@set@math@font@step
}
\newcommand\sjtu@load@fontset@termes{%
  \sjtu@set@font@termes
  \sjtu@set@font@texgyre@sans@mono
  \sjtu@set@math@font@termes
}
\newcommand\sjtu@load@fontset@pagella{%
  \sjtu@set@font@pagella
  \sjtu@set@font@texgyre@sans@mono
  \sjtu@set@math@font@pagella
}
\newcommand\sjtu@load@fontset@cambria{%
  \sjtu@set@font@cambria
  \sjtu@set@math@font@cambria
}
\newcommand\sjtu@load@fontset@libertinus{%
  \sjtu@set@font@libertinus
  \sjtu@set@math@font@libertinus
}
\newcommand\sjtu@load@fontset@lm{%
  \sjtu@set@font@lm
  \sjtu@set@math@font@lm
}
\newcommand\sjtu@load@fontset@none{\relax}
%    \end{macrocode}
%
% 载入西文字体集。
%    \begin{macrocode}
% \newcommand\sjtu@load@fontset{%
%   \@nameuse{sjtu@load@fontset@\sjtu@latinfontset}
% }
% latinfontset 是csu的key 使用 对应的kv接口调用  现在没有实现
% \sjtu@load@fontset@latinfontset
\sjtu@load@fontset@times
% linux系统请使用 stix
% \sjtu@load@fontset@stix
% \sjtu@option@hook{sjtu}{latinfontset}{%
%   \sjtu@load@fontset
% }


\newcommand\csu@pdfbookmark[2]{}
% 定义通用的chapter命令
\NewDocumentCommand{\csu@chapter}{s m}{
  \if@openright\cleardoublepage\else\clearpage\fi
%   \addcontentsline{toc}{chapter}{#1}%
%   \csu@pdfbookmark{0}{#1}
  \IfBooleanTF{#1}{
      \csu@pdfbookmark{0}{#2}
  }{
      \addcontentsline{toc}{chapter}{#2}
  }
  \chapter*{#2}
}


\AtEndOfPackageFile*{hyperref}{
  \hypersetup{
    linktoc            = all,
    bookmarksdepth     = 2,
    bookmarksnumbered  = true,
    bookmarksopen      = true,
    bookmarksopenlevel = 1,
    unicode            = true,
    psdextra           = true,
    breaklinks         = true,
    plainpages         = false,
    pdfdisplaydoctitle = true,
    hidelinks,
  }
  \newcounter{csu@bookmark}
  \renewcommand\csu@pdfbookmark[2]{%
    \phantomsection
    \stepcounter{csu@bookmark}%
    \pdfbookmark[#1]{#2}{csuchapter.\thecsu@bookmark}%
  }
%   \renewcommand\sjtu@phantomsection{%
%     \phantomsection
%   }
%   \pdfstringdefDisableCommands{%
%     \let\\\@empty
%     \let\quad\@empty
%     \let\hspace\@gobble
%   }
%   \@ifpackagelater{hyperref}{2019/04/27}{}{%
%     \g@addto@macro\psdmapshortnames{\let\mu\textmu}
%   }%
%   \AtBeginDocument{%
%     \hypersetup{
%       pdftitle    = \sjtu@info@title,
%       pdfsubject  = \sjtu@name@subject,
%       pdfkeywords = \sjtu@info@keywords,
%       pdfauthor   = \sjtu@info@author,
%       pdfcreator  = {LaTeX with SJTUThesis \version}
%     }
%   }%
}


\endinput
