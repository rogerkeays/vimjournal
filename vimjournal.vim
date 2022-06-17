
autocmd BufRead,BufNewFile *.log setl filetype=journal

" jump to the end of the file when first loaded
function JumpEnd()
  if !exists("b:vimjournal_jumped")
    let b:vimjournal_jumped = 1
    normal G
  endif
endfunction
autocmd BufWinEnter *.log call JumpEnd()

"autocmd FileType journal
autocmd BufRead *.log setl foldmethod=expr foldtext=getline(v:foldstart) fillchars= 
autocmd BufRead *.log setl foldexpr=strcharpart(getline(v\:lnum),19,1)=='│'?'>1'\:1
autocmd BufRead *.log setl autoindent sw=2 ts=8 nrformats=
autocmd BufRead *.log setl wrap linebreak breakindent showbreak=>\ 

" more accurate regexp for folding, but much slower
"autocmd BufRead *.log setl foldexpr=getline(v\:lnum)=~'^[0-9A-Za-z]\\{8\\}_[0-9A-Za-z]\\{4\\}.*[│\|]'?'>1'\:1

" commands to jump to a random line for flashcard-like quizzes
autocmd BufRead *.log command! RandomMatch execute 'normal! '.matchstr(system('grep -n -o "'.@/.'" '.expand('%:p').' | shuf -n 1'), '^[0-9]*').'G'
autocmd BufRead *.log command! RandomLine execute 'normal! '.matchstr(system('od -vAn -N3 -tu4 /dev/urandom'), '^\_s*\zs.\{-}\ze\_s*$') % line('$').'G'

autocmd BufRead *.log nnoremap <TAB> za
autocmd BufRead *.log nnoremap <C-t> Go<C-R>=strftime("%Y%m%d_%H%M")<CR> KEP  │ <ESC>zm1GGA
autocmd BufRead *.log nnoremap <C-o> yyp:s/.│.*/ │ <CR>A
autocmd BufRead *.log nnoremap <C-l> :Explore /home/guybrush/journal/history<CR>
"autocmd BufRead *.log nnoremap ç 020l<C-a>:w<CR>zc:RandomMatch<CR>zz
"autocmd BufRead *.log nnoremap Ç 020lr0:w<CR>zc:RandomMatch<CR>zz
autocmd BufRead *.log nnoremap º 020l<C-a>:w<CR>zcnzz
autocmd BufRead *.log nnoremap ª 020lr0:w<CR>zcnzz
autocmd BufRead *.log inoremap <C-t> // <C-R>=strftime("%Y%m%d_%H%M")<CR> 
autocmd BufRead *.log inoremap <C-b> │
autocmd BufRead *.log inoremap <C-x> ✘
autocmd BufRead *.log inoremap <C-z> ✔

"
" tagging scheme:
"   / category        (file)
"   + person          (global)
"   # topic           (global)
"   = project         (global)
"   ! problem, goal   (global)
"   > context         (entry)
"   @ place           (global)
"   : data, url       (entry)
"
autocmd BufRead *.log syn match Tags " [/+#=!>@:][^/+#=!>@: ].*" contained

autocmd BufRead *.log syn keyword Bar │ contained
autocmd BufRead *.log syn match Date "^[0-9A-Za-z]\{8\}[!_][0-9A-Za-z]\{4\}[!. ]... .*\ze.[│|]" contained
autocmd BufRead *.log syn match NoStars "^[0-9A-Za-z]\{8\}[!_][0-9A-Za-z]\{4\}.*[│|].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match OneStar "^[0-9A-Za-z]\{8\}[!_][0-9A-Za-z]\{4\}.*[x/1][│|].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match TwoStar "^[0-9A-Za-z]\{8\}[!_][0-9A-Za-z]\{4\}.*[-2][│|].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match ThreeStar "^[0-9A-Za-z]\{8\}[!_][0-9A-Za-z]\{4\}.*[=~3][│|].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match FourStar "^[0-9A-Za-z]\{8\}[!_][0-9A-Za-z]\{4\}.*[+·4][│|].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match FiveStar "^[0-9A-Za-z]\{8\}[!_][0-9A-Za-z]\{4\}.*[*5][│|].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match Heading "^==[^ ].*$"
autocmd BufRead *.log syn match Heading "^## .*$"
autocmd BufRead *.log syn match Comment "^//.*$"
autocmd BufRead *.log syn match Comment " // .*$"

autocmd BufRead *.log hi Bar ctermfg=lightgrey
autocmd BufRead *.log hi Date ctermfg=lightgrey
autocmd BufRead *.log hi Tags ctermfg=235
autocmd BufRead *.log hi NoStars ctermfg=lightgrey
autocmd BufRead *.log hi OneStar ctermfg=brown
autocmd BufRead *.log hi TwoStar ctermfg=red
autocmd BufRead *.log hi ThreeStar ctermfg=blue
autocmd BufRead *.log hi FourStar ctermfg=green
autocmd BufRead *.log hi FiveStar ctermfg=yellow
autocmd BufRead *.log hi Heading ctermfg=white
autocmd BufRead *.log hi Comment ctermfg=lightgreen
autocmd BufRead *.log hi Reference ctermfg=lightyellow

" include tag symbols and hyphens in autocomplete
autocmd BufRead *.log setl iskeyword+=@-@,/,#,=,!,>,:,-

" recognise _ in datetimes as a word boundary
autocmd BufRead *.log setl iskeyword-=_

" only autocomplete on the current buffer to improve performance
autocmd BufRead *.log setl complete=.

" disable the autocomplete menu
autocmd BufRead *.log setl completeopt=

