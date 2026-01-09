import Server from 'rhettjs/server';

// ============================================
// MINECRAFT BOOK TOOLS FOR JAVA 1.21.1
// ============================================

// Character widths in pixels (including spacing)
const CHAR_WIDTHS = {
  ' ': 4, '!': 2, '"': 5, '#': 6, '$': 6, '%': 6, '&': 6, "'": 3,
  '(': 4, ')': 4, '*': 5, '+': 6, ',': 2, '-': 6, '.': 2, '/': 6,
  '0': 6, '1': 6, '2': 6, '3': 6, '4': 6, '5': 6, '6': 6, '7': 6,
  '8': 6, '9': 6, ':': 2, ';': 2, '<': 5, '=': 6, '>': 5, '?': 6,
  '@': 7, 'A': 6, 'B': 6, 'C': 6, 'D': 6, 'E': 6, 'F': 6, 'G': 6,
  'H': 6, 'I': 4, 'J': 6, 'K': 6, 'L': 6, 'M': 6, 'N': 6, 'O': 6,
  'P': 6, 'Q': 6, 'R': 6, 'S': 6, 'T': 6, 'U': 6, 'V': 6, 'W': 6,
  'X': 6, 'Y': 6, 'Z': 6, '[': 4, '\\': 6, ']': 4, '^': 6, '_': 6,
  '`': 3, 'a': 6, 'b': 6, 'c': 6, 'd': 6, 'e': 6, 'f': 5, 'g': 6,
  'h': 6, 'i': 2, 'j': 6, 'k': 5, 'l': 3, 'm': 6, 'n': 6, 'o': 6,
  'p': 6, 'q': 6, 'r': 6, 's': 6, 't': 4, 'u': 6, 'v': 6, 'w': 6,
  'x': 6, 'y': 6, 'z': 6, '{': 5, '|': 2, '}': 5, '~': 7
};

const DEFAULT_CHAR_WIDTH = 6;
const MAX_LINE_WIDTH = 114;
const BOLD_EXTRA_WIDTH = 1;

// Get width of single character
const getCharWidth = (char, isBold = false) => {
  const baseWidth = CHAR_WIDTHS[char] || DEFAULT_CHAR_WIDTH;
  return isBold ? baseWidth + BOLD_EXTRA_WIDTH : baseWidth;
};

// Calculate total width of text
const getLineWidth = (text, isBold = false) => {
  return text.split('').reduce((total, char) => {
    return total + getCharWidth(char, isBold);
  }, 0);
};

// Wrap text to fit line width
const wrapText = (text, maxWidth = MAX_LINE_WIDTH, isBold = false) => {
  const words = text.split(' ');
  const lines = [];
  let currentLine = '';
  let currentWidth = 0;

  for (const word of words) {
    const wordWidth = getLineWidth(word, isBold);
    const spaceWidth = currentLine ? getCharWidth(' ', isBold) : 0;
    const totalWidth = currentWidth + spaceWidth + wordWidth;

    if (totalWidth <= maxWidth) {
      currentLine += (currentLine ? ' ' : '') + word;
      currentWidth = totalWidth;
    } else {
      if (currentLine) lines.push(currentLine);
      currentLine = word;
      currentWidth = wordWidth;
    }
  }

  if (currentLine) lines.push(currentLine);
  return lines;
};

// Create text component
const createText = (text, color = 'black', bold = false, italic = false) => {
  return { text, color, bold, italic };
};

// Create clickable link
const createLink = (text, targetPage, color = 'black') => {
  return {
    text,
    color,
    clickEvent: { action: 'change_page', value: String(targetPage) },
    hoverEvent: { action: 'show_text', contents: { text: `Go to page ${targetPage}` } }
  };
};

// Convert to JSON page
const toPage = (components) => {
  const arr = Array.isArray(components) ? components : [components];
  return JSON.stringify(arr).replace(/\\n/g, '\\\\n');
};

// ============================================
// EXPORTS
// ============================================

export function makeBook(name = '@s') {
  const page1 = [
    createText('Admin Book\n', 'black', true),
    createText('Welcome!\n\n', 'black'),
    createLink('Click for Page 2', 2, 'blue')
  ];

  const page2 = [
    createText('Page 2\n\n', 'black', true),
    createText('Content here.\n\n', 'black'),
    createLink('[← Back]', 1, 'gray')
  ];

  const pages = [page1, page2].map(toPage);
  const cmd = `give ${name} written_book[written_book_content={pages:[${pages.map(p => `'${p}'`).join(',')}],title:"Admin",author:"Server"}]`;

  Server.runCommand(cmd);
}

export function testCharWidth(name = '@s') {
  const tests = [':', 'N', 'i', 'a'];
  const comps = [createText('Width Test\n\n', 'black', true)];

  tests.forEach(char => {
    const w = getCharWidth(char);
    const n = Math.floor(114 / w);
    const line = char.repeat(n);

    comps.push(createText(`'${char}' (${w}px):\n`, 'gray'));
    comps.push(createText(line + '\n', 'black'));

    console.log(`${char}: ${w}px, ${n} chars = ${getLineWidth(line)}px`);
  });

  const page = toPage(comps);
  const cmd = `give ${name} written_book[written_book_content={pages:['${page}'],title:"Test",author:"Test"}]`;

  Server.runCommand(cmd);
}

// console.log('Book tools ready: makeBook(name), testCharWidth(name)');